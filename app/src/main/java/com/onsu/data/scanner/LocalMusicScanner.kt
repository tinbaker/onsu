package com.onsu.data.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalMusicScanner {
    fun hasAudioPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanMusic(context: Context): List<Track> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission(context)) return@withContext emptyList()

        val tracks = mutableListOf<Track>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        // IS_MUSIC excludes recordings, sound clips and some downloaded songs.
        // Query all indexed audio, not only files tagged as music or in Music/.
        try {
            val cursor = context.contentResolver.query(
                collection, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC"
            ) ?: error("MediaStore không trả về danh sách âm thanh.")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn)?.takeIf(String::isNotBlank)
                        ?: it.getString(nameColumn)?.takeIf(String::isNotBlank)
                        ?: tr("Unknown title", "Không rõ tên bài")
                    val artist = it.getString(artistColumn)?.takeUnless { value ->
                        value.isBlank() || value == "<unknown>"
                    } ?: tr("Local artist", "Nghệ sĩ cục bộ")
                    val album = it.getString(albumColumn)?.takeIf(String::isNotBlank)
                        ?: tr("Device storage", "Bộ nhớ máy")
                    val duration = it.getLong(durationColumn).coerceAtLeast(0L)
                    val size = if (it.isNull(sizeColumn)) null else it.getLong(sizeColumn)

                    // Missing duration metadata and short/small clips are not proof
                    // of corruption. Let the decoder determine codec support.
                    val isPlayable = size == null || size != 0L
                    tracks.add(
                        Track(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            genre = "Local Audio",
                            source = TrackSource.LOCAL,
                            sourceId = id.toString(),
                            audioUrl = ContentUris.withAppendedId(collection, id).toString(),
                            isPlayable = isPlayable,
                            unplayableReason = if (isPlayable) null else tr(
                                "Empty audio file (0 bytes)",
                                "Tệp âm thanh rỗng (0 byte)"
                            ),
                            fileSizeBytes = (size ?: 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Log.e("OnsuLocalMusic", "Cannot scan device audio", error)
            throw error
        }
        tracks
    }
}
