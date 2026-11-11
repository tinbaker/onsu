package com.onsu.data.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.onsu.i18n.tr
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thông tin cần để đặt một tệp âm thanh vào thư viện nhạc của máy. */
data class PublishRequest(
    val displayName: String,
    val mimeType: String,
    val title: String,
    val artist: String,
    val album: String
)

/** Kết quả sau khi tệp đã nằm trong bộ nhớ dùng chung. */
data class PublishedTrack(
    val uri: Uri,
    val displayName: String,
    val fileSizeBytes: Long
)

/**
 * Đưa tệp tải về ra khỏi bộ nhớ riêng của app.
 *
 * Tách thành interface để test không phải chạm vào MediaStore thật, và để phần
 * "tải byte" không phụ thuộc vào API lưu trữ của từng phiên bản Android.
 */
interface DownloadPublisher {
    suspend fun publish(tempFile: File, request: PublishRequest): PublishedTrack

    /** Tệp còn thật sự đọc được không (người dùng có thể đã xoá bằng ứng dụng khác). */
    suspend fun exists(uri: Uri): Boolean

    suspend fun delete(uri: Uri): Boolean
}

/**
 * Ghi bài đã tải vào thư mục `Music/Onsu` của máy, để:
 *  - người dùng thấy tệp trong ứng dụng quản lý tệp và trong các app nghe nhạc khác;
 *  - tệp vẫn còn khi gỡ app (không nằm trong `Android/data/<package>`);
 *  - ExoPlayer phát được bằng `content://` khi không có mạng.
 *
 * Android 10 trở lên dùng MediaStore (không cần quyền ghi). Android 9 trở xuống
 * không có scoped storage nên phải ghi trực tiếp và cần `WRITE_EXTERNAL_STORAGE`.
 */
class MediaStorePublisher(private val context: Context) : DownloadPublisher {

    override suspend fun publish(tempFile: File, request: PublishRequest): PublishedTrack =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishWithMediaStore(tempFile, request)
            } else {
                publishToMusicFolder(tempFile, request)
            }
        }

    override suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == FILE_SCHEME) {
                val file = uri.path?.let(::File) ?: return@withContext false
                file.exists() && file.length() > 0L
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length > 0L } ?: false
            }
        } catch (error: Exception) {
            false
        }
    }

    override suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == FILE_SCHEME) {
                val file = uri.path?.let(::File) ?: return@withContext false
                val deleted = !file.exists() || file.delete()
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                deleted
            } else {
                // Chỉ xoá được mục do chính app này tạo ra; mục của app khác sẽ trả 0.
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (error: Exception) {
            false
        }
    }

    private fun publishWithMediaStore(tempFile: File, request: PublishRequest): PublishedTrack {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, request.displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, request.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, DownloadStore.RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, request.title)
            put(MediaStore.Audio.Media.ARTIST, request.artist)
            // IS_PENDING giấu tệp đi trong lúc ghi, để app khác không đọc phải tệp dở.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw DownloadException(
                tr("Couldn't create a file in the device music folder.", "Không tạo được tệp trong thư mục nhạc của máy.")
            )

        try {
            val output = resolver.openOutputStream(uri)
                ?: throw DownloadException(
                    tr("Couldn't write the file to the device music folder.", "Không ghi được tệp vào thư mục nhạc của máy.")
                )
            output.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out, COPY_BUFFER) }
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }

        val publish = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)

        return PublishedTrack(uri, request.displayName, tempFile.length())
    }

    private fun publishToMusicFolder(tempFile: File, request: PublishRequest): PublishedTrack {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw DownloadException(
                "Cần quyền ghi bộ nhớ để lưu bài vào thư mục Music của máy. Hãy cấp quyền rồi thử lại."
            )
        }

        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val directory = File(root, DownloadStore.FOLDER_NAME)
        if (!directory.exists() && !directory.mkdirs()) {
            throw DownloadException(
                tr("Couldn't create the Music/${DownloadStore.FOLDER_NAME} folder.", "Không tạo được thư mục Music/${DownloadStore.FOLDER_NAME}.")
            )
        }

        val target = File(directory, request.displayName)
        tempFile.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(request.mimeType),
            null
        )
        return PublishedTrack(Uri.fromFile(target), request.displayName, target.length())
    }

    private companion object {
        const val FILE_SCHEME = "file"
        const val COPY_BUFFER = 64 * 1024
    }
}
