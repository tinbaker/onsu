package com.onsu.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.onsu.data.models.Track

object MediaItemBuilder {
    /**
     * @param localUri tệp đã tải về máy (`content://…` trong thư mục `Music/Onsu`);
     *   khi có, player đọc thẳng từ máy thay vì gọi mạng — đây là cơ chế phát ngoại tuyến.
     */
    fun fromTrack(track: Track, localUri: Uri? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setGenre(track.genre)
            .apply {
                if (!track.artworkUrl.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(track.artworkUrl))
                }
            }
            .build()

        val uri = localUri ?: Uri.parse(track.audioUrl)

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
