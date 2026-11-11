package com.onsu.data.models

enum class TrackSource(val label: String, val badge: String) {
    LOCAL("Trong máy", "📁 MÁY"),
    JAMENDO("Từ mạng — Jamendo", "🌐 JAMENDO"),
    AUDIUS("Từ mạng — Audius", "🌐 AUDIUS")
}

/**
 * Mức "gu" của hàng đợi. Nhãn hiển thị nằm ở lớp i18n (`AppStrings.tasteLabel`)
 * để đổi ngôn ngữ không cần khởi động lại app.
 */
enum class TasteLevel {
    FAMILIAR,
    BALANCED,
    EXPLORE
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val artworkUrl: String? = null,
    val durationMs: Long,
    val genre: String,
    val year: Int = 2024,
    val source: TrackSource,
    val sourceId: String = id,
    val audioUrl: String = "",
    val isPlayable: Boolean = true,
    val unplayableReason: String? = null,
    val fileSizeBytes: Long = 0L,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val tags: List<String> = emptyList()
) {
    val durationFormatted: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val fileSizeMbFormatted: String
        get() {
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            return "%.1f MB".format(mb)
        }
}

data class Playlist(
    val id: String,
    val title: String,
    val trackIds: List<String> = emptyList(),
    val isPrivate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val id: String = "guest_01",
    val email: String = "guest@mixtape.local",
    val displayName: String = "Người nghe",
    val photoUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val isGuest: Boolean = true
)

data class PlaybackSession(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
    val source: TrackSource,
    val sourceId: String,
    val durationMs: Long,
    val genre: String,
    val positionMs: Long,
    val playlistMode: TasteLevel
)
