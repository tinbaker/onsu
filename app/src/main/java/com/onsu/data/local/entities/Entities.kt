package com.onsu.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mọi bảng dưới đây đều có `ownerId` — id của tài khoản đang sở hữu dữ liệu.
 *
 * Trước đây các bảng này không có cột chủ sở hữu và DAO đọc toàn bộ bảng, nên đổi tài
 * khoản vẫn thấy chung một thư viện. `ownerId` lấy từ `users.id` khi đã đăng nhập, hoặc
 * hằng số [GUEST_OWNER_ID] khi dùng chế độ khách.
 */
const val GUEST_OWNER_ID = "guest"

@Entity(tableName = "playback_session")
data class PlaybackSessionEntity(
    /** Mỗi tài khoản có phiên nghe dở riêng. */
    @PrimaryKey val ownerId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val source: String,
    val sourceId: String,
    val durationMs: Long,
    val genre: String,
    val positionMs: Long,
    val playlistMode: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "liked_tracks",
    primaryKeys = ["ownerId", "trackId"]
)
data class LikedTrackEntity(
    val ownerId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val source: String,
    val sourceId: String,
    val durationMs: Long,
    val genre: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val isPrivate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Bảng con của `playlists`: quyền sở hữu đi theo playlist cha nên không cần `ownerId`.
 * Mọi truy vấn vẫn phải join sang `playlists` để kiểm tra chủ sở hữu.
 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "position"]
)
data class PlaylistItemEntity(
    val playlistId: String,
    val position: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val source: String,
    val sourceId: String,
    val durationMs: Long,
    val genre: String
)

/**
 * Bài hát online đã tải về máy để nghe ngoại tuyến.
 *
 * Bảng này **không** khoá theo `ownerId` như like/playlist/gu nhạc: tệp âm thanh nằm
 * trên bộ nhớ thiết bị, ai mở app cũng phát được, nên giấu theo tài khoản chỉ làm
 * người dùng mất dung lượng mà không thấy bài đâu. Đây là cùng cách xử lý với màn
 * "Nhạc trong máy" — dữ liệu của thiết bị, không phải thư viện cá nhân.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val source: String,
    val sourceId: String,
    val durationMs: Long,
    val genre: String,
    /** Tên tệp trong thư mục `Music/Onsu` của máy. */
    val fileName: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    /**
     * `Uri` của tệp trong thư mục nhạc của máy (`content://…` từ Android 10,
     * `file://…` trên máy cũ). Rỗng với bản ghi tạo từ v3 — những bản ghi đó được
     * chuyển sang thư mục Music ở lần mở app kế tiếp.
     */
    val contentUri: String = "",
    val displayName: String = ""
)

@Entity(tableName = "taste_profile")
data class TasteProfileEntity(
    /** Gu nhạc cũng phải riêng: tài khoản mới không được thừa hưởng seed của người trước. */
    @PrimaryKey val ownerId: String,
    val selectedArtists: String, // comma-separated
    val selectedGenres: String,  // comma-separated
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Bảng `users` chỉ giữ **một** dòng: tài khoản đang đăng nhập trên máy này.
 * Thư viện của từng người nằm ở các bảng trên, khoá theo `ownerId`.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val isLoggedIn: Boolean
)
