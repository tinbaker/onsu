package com.onsu.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.i18n.tr
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadOutcome {
    /**
     * @param uri trỏ tới tệp thật trong thư mục nhạc của máy (`content://` hoặc `file://`).
     */
    data class Success(
        val uri: Uri,
        val displayName: String,
        val fileSizeBytes: Long
    ) : DownloadOutcome

    data class Failure(val message: String) : DownloadOutcome
}

/** Lỗi có sẵn câu tiếng Việt để hiện thẳng cho người dùng. */
class DownloadException(message: String) : IOException(message)

/**
 * Tách phần "lấy byte" khỏi phần "kiểm tra / đặt tên / đưa vào thư viện máy" để test
 * được toàn bộ luồng tải mà không cần mạng.
 */
interface AudioFetcher {
    /** Ghi luồng âm thanh vào [target]; trả về `Content-Type` do máy chủ công bố. */
    suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit): String?
}

class OkHttpAudioFetcher(
    private val client: OkHttpClient = defaultClient()
) : AudioFetcher {

    override suspend fun fetch(
        url: String,
        target: File,
        onProgress: (Float) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    tr(
                        "The music server returned error ${response.code}. Try again later.",
                        "Máy chủ nhạc trả về lỗi ${response.code}. Thử lại sau."
                    )
                )
            }
            val body = response.body
                ?: throw DownloadException(
                    tr(
                        "The music server returned no audio data.",
                        "Máy chủ không trả về dữ liệu âm thanh."
                    )
                )

            val total = body.contentLength()
            var written = 0L
            var lastReportedPercent = -1

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt()
                            // Báo theo bước 5% để không đẩy quá nhiều lần cập nhật UI.
                            if (percent != lastReportedPercent && percent % 5 == 0) {
                                lastReportedPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (written == 0L) throw DownloadException(
                tr("The downloaded file was empty. Try again later.", "Tệp tải về rỗng. Thử lại sau.")
            )
            response.header("Content-Type")
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "OnsuMusic/1.0 (Android; Media3)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

/**
 * Tải một bài online về máy.
 *
 * Byte được ghi vào tệp tạm trong bộ nhớ riêng của app, kiểm tra xong mới đưa sang
 * thư mục `Music/Onsu` của máy qua [DownloadPublisher]. Nhờ vậy một lần tải bị ngắt
 * giữa đường không để lại tệp hỏng trong thư viện nhạc, và bản ghi Room chỉ được thêm
 * sau khi tệp đã nằm đúng chỗ.
 */
class TrackDownloader(
    private val context: Context,
    private val fetcher: AudioFetcher = OkHttpAudioFetcher(),
    private val publisher: DownloadPublisher = MediaStorePublisher(context)
) {
    suspend fun download(track: Track, onProgress: (Float) -> Unit = {}): DownloadOutcome {
        if (track.source == TrackSource.LOCAL) {
            return DownloadOutcome.Failure("Bài này đã nằm trong máy rồi.")
        }
        if (track.audioUrl.isBlank()) {
            return DownloadOutcome.Failure("Bài này không có đường dẫn phát trực tuyến.")
        }

        val directory = DownloadStore.cacheDirectory(context)
        val temp = File(directory, "${DownloadStore.sanitize(track.id)}.part")
        temp.delete()

        return try {
            val contentType = fetcher.fetch(track.audioUrl, temp) { onProgress(it) }

            if (!looksLikeAudio(temp)) {
                temp.delete()
                return DownloadOutcome.Failure(
                    "Máy chủ trả về dữ liệu không phải âm thanh — bài có thể đã bị gỡ."
                )
            }

            val extension = extensionFor(contentType, track.audioUrl)
            val request = PublishRequest(
                displayName = DownloadStore.fileName(track.artist, track.title, extension),
                mimeType = mimeTypeFor(extension),
                title = track.title,
                artist = track.artist,
                album = track.album
            )
            val published = publisher.publish(temp, request)
            temp.delete()

            onProgress(1f)
            // Đăng ký ngay để lần phát kế tiếp đọc từ tệp trên máy; repository ghi bản
            // ghi Room tương ứng để danh sách "Đã tải" còn nguyên sau khi mở lại app.
            DownloadStore.register(track.id, published.uri.toString())
            DownloadOutcome.Success(published.uri, published.displayName, published.fileSizeBytes)
        } catch (error: CancellationException) {
            temp.delete()
            throw error
        } catch (error: Exception) {
            temp.delete()
            Log.w(TAG, "Download failed for ${track.id}: ${error.message}", error)
            DownloadOutcome.Failure(friendlyMessage(error))
        }
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is DownloadException -> error.message ?: tr(
            "Couldn't download this track. Try again later.",
            "Tải bài này thất bại. Thử lại sau."
        )
        is IOException -> tr(
            "Couldn't reach the music server. Check your connection and try again.",
            "Không kết nối được tới máy chủ nhạc. Kiểm tra mạng rồi thử lại."
        )
        else -> tr(
            "Couldn't download this track. Try again later.",
            "Tải bài này thất bại. Thử lại sau."
        )
    }

    /**
     * Audius trả về JSON lỗi kèm HTTP 200 trong vài trường hợp, nên phải xem nội dung
     * thật chứ không tin mã trạng thái.
     */
    private fun looksLikeAudio(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_AUDIO_BYTES) return false
        val head = ByteArray(HEAD_SNIFF_BYTES)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0) return false
        val firstChar = head.take(read)
            .map { it.toInt().toChar() }
            .firstOrNull { !it.isWhitespace() }
            ?: return false
        return firstChar != '{' && firstChar != '<'
    }

    companion object {
        private const val TAG = "TrackDownloader"
        private const val MIN_AUDIO_BYTES = 1024L
        private const val HEAD_SNIFF_BYTES = 64

        private val KNOWN_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

        /**
         * Đuôi tệp chỉ để dễ nhận biết: ExoPlayer nhận dạng codec bằng nội dung, nên
         * một đuôi sai không làm bài không phát được. Nhưng ứng dụng nhạc khác thì dựa
         * vào đuôi, nên vẫn phải suy cho đúng.
         */
        internal fun extensionFor(contentType: String?, url: String): String {
            val type = contentType?.lowercase().orEmpty()
            return when {
                type.contains("mpeg") || type.contains("mp3") -> "mp3"
                type.contains("mp4") || type.contains("aac") || type.contains("m4a") -> "m4a"
                type.contains("ogg") || type.contains("opus") -> "ogg"
                type.contains("wav") -> "wav"
                type.contains("flac") -> "flac"
                type.contains("webm") -> "weba"
                else -> url.substringBefore('?').substringAfterLast('.', "").lowercase()
                    .takeIf { it in KNOWN_EXTENSIONS } ?: "mp3"
            }
        }

        /** `MIME_TYPE` ghi vào MediaStore; sai loại thì vài app nhạc không thấy bài. */
        internal fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "weba", "webm" -> "audio/webm"
            else -> "audio/mpeg"
        }
    }
}
