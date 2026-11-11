package com.onsu.data.download

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bảng tra cứu "bài này đã có tệp trên máy hay chưa" + cách đặt tên tệp.
 *
 * Từ bản này tệp không còn nằm trong bộ nhớ riêng của app: nó được đưa vào thư mục
 * `Music/Onsu` của máy (xem [MediaStorePublisher]), nên giá trị lưu lại là `Uri`
 * (`content://…` trên Android 10+, `file://…` trên máy cũ) chứ không phải `File`.
 *
 * [uris] là bản sao trong RAM, nạp từ Room lúc khởi động
 * (`MusicRepository.refreshDownloadRegistry`) để `MediaItemBuilder` chọn được tệp
 * cục bộ mà không phải truy vấn database trên main thread.
 */
object DownloadStore {
    /** Tên thư mục con trong `Music/` của máy. */
    const val FOLDER_NAME = "Onsu"

    /** Đường dẫn tương đối dùng cho MediaStore (Android 10+). */
    const val RELATIVE_PATH = "Music/$FOLDER_NAME"

    private const val MAX_NAME_LENGTH = 80
    private const val TEMP_DIR = "onsu_download"

    private val uris = ConcurrentHashMap<String, String>()

    /** Tên tệp chỉ gồm ký tự an toàn, dùng cho tệp tạm và id lạ từ API. */
    fun sanitize(value: String): String {
        val cleaned = value.map { char ->
            // Không cho '.' để id kiểu "../../x" không tạo ra đường dẫn thoát thư mục.
            if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_'
        }.joinToString("")
        return cleaned.take(MAX_NAME_LENGTH).ifBlank { "track" }
    }

    /**
     * Tên tệp hiển thị trong ứng dụng quản lý tệp: `Nghệ sĩ - Tên bài.mp3`.
     *
     * Giữ dấu tiếng Việt (người dùng đọc được), chỉ thay những ký tự hệ thống tệp
     * không cho phép.
     */
    fun fileName(artist: String, title: String, extension: String): String {
        val raw = listOf(artist.trim(), title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "Onsu" }
        val safe = raw.map { char ->
            if (char.isLetterOrDigit() || char == ' ' || char == '-' || char == '_' || char == '.') {
                char
            } else {
                '_'
            }
        }.joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)
            .ifBlank { "Onsu" }
        return "$safe.$extension"
    }

    /** Thư mục tạm (bộ nhớ riêng) — chỉ tồn tại trong lúc tải. */
    fun cacheDirectory(context: Context): File =
        File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }

    /**
     * Tệp của bản ghi v3 (nằm trong bộ nhớ riêng của app).
     * Chỉ dùng để chuyển những bài đã tải trước khi nâng cấp sang thư mục Music.
     */
    fun legacyFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        return File(File(base, LEGACY_DIR), fileName)
    }

    fun register(trackId: String, uri: String) {
        uris[trackId] = uri
    }

    fun unregister(trackId: String) {
        uris.remove(trackId)
    }

    /** `Uri` của tệp đã tải, null nếu bài chưa được tải về máy. */
    fun uriFor(trackId: String): Uri? = uris[trackId]?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    fun clear() {
        uris.clear()
    }

    fun snapshot(): Map<String, String> = HashMap(uris)

    private const val LEGACY_DIR = "onsu_downloads"
}
