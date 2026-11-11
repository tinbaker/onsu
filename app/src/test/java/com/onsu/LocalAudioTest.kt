package com.onsu

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.data.repository.MusicRepository
import com.onsu.data.scanner.LocalMusicScanner
import com.onsu.player.PlaybackService
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalAudioTest {
    private lateinit var context: Context
    private lateinit var provider: AudioProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(AudioProvider::class.java).create("media").get()
        ShadowContentResolver.registerProviderInternal("media", provider)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
    }

    @Test
    fun `no audio permission does not query or crash`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertFalse(LocalMusicScanner.hasAudioPermission(context))
        assertTrue(LocalMusicScanner.scanMusic(context).isEmpty())
        assertEquals(0, provider.queryCount)
    }

    @Test
    @Config(sdk = [32])
    fun `Android 12 uses storage permission rather than Android 13 audio permission`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertFalse(LocalMusicScanner.hasAudioPermission(context))
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertTrue(LocalMusicScanner.hasAudioPermission(context))
        assertEquals(5, LocalMusicScanner.scanMusic(context).size)
    }

    @Test
    fun `scanner includes recordings short clips and missing duration metadata`() = runTest {
        val tracks = LocalMusicScanner.scanMusic(context)
        assertEquals(5, tracks.size)
        assertNull("Must not filter by IS_MUSIC", provider.lastSelection)
        assertTrue(tracks.first { it.id == "local_2" }.isPlayable)
        assertTrue(tracks.first { it.id == "local_3" }.isPlayable)
        assertTrue(tracks.first { it.id == "local_4" }.isPlayable)
        assertEquals("recording.m4a", tracks.first { it.id == "local_3" }.title)
        assertTrue(tracks.all { it.audioUrl.startsWith("content://media/external/audio/media/") })
    }

    @Test
    fun `grant after cached denied scan refreshes device audio without fetching remote again`() = runTest {
        var remoteFetches = 0
        val repository = MusicRepository(context, fetchRemoteCatalog = {
            remoteFetches++
            (1..85).map { track(it, TrackSource.AUDIUS) }
        })
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertTrue(repository.loadCandidatePool().none { it.source == TrackSource.LOCAL })
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertEquals(5, repository.loadCandidatePool().count { it.source == TrackSource.LOCAL })
        assertEquals(1, remoteFetches)
        assertEquals(1, provider.queryCount)
        repository.loadCandidatePool()
        assertEquals(1, provider.queryCount)
        repository.loadCandidatePool(forceRefresh = true)
        assertEquals(2, provider.queryCount)
        assertEquals(1, remoteFetches)
    }

    @Test
    fun `revoking permission invalidates the cached device library`() = runTest {
        val repository = MusicRepository(context, fetchRemoteCatalog = {
            (1..85).map { track(it, TrackSource.AUDIUS) }
        })
        assertEquals(5, repository.loadCandidatePool().count { it.source == TrackSource.LOCAL })
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertTrue(repository.loadCandidatePool().none { it.source == TrackSource.LOCAL })
    }

    @Test
    fun `zero byte audio is visible but not playable`() = runTest {
        val track = LocalMusicScanner.scanMusic(context).first { it.id == "local_5" }
        assertFalse(track.isPlayable)
        assertNotNull(track.unplayableReason)
    }

    @Test
    fun `service data source can read a content URI without HTTP`() {
        val payload = "local-audio-content".toByteArray()
        val file = File.createTempFile("onsu-content-", ".wav", context.cacheDir)
        file.writeBytes(payload)
        provider.audioFile = file
        val source = PlaybackService.createDataSourceFactory(context).createDataSource()
        try {
            source.open(DataSpec(Uri.parse("content://media/external/audio/media/1")))
            val buffer = ByteArray(payload.size)
            assertEquals(payload.size, source.read(buffer, 0, buffer.size))
            assertArrayEquals(payload, buffer)
        } finally {
            source.close()
            file.delete()
        }
    }

    @Test
    fun `service data source also reads file URIs`() {
        val payload = "local-file-audio".toByteArray()
        val file = File.createTempFile("onsu-file-", ".wav", context.cacheDir)
        file.writeBytes(payload)
        val source = PlaybackService.createDataSourceFactory(context).createDataSource()
        try {
            source.open(DataSpec(Uri.fromFile(file)))
            val buffer = ByteArray(payload.size)
            assertEquals(payload.size, source.read(buffer, 0, buffer.size))
            assertArrayEquals(payload, buffer)
        } finally {
            source.close()
            file.delete()
        }
    }

    private fun track(index: Int, source: TrackSource) = Track(
        id = "${source.name}_$index",
        title = "Audio $index",
        artist = "Artist $index",
        durationMs = 1000,
        genre = "Local Audio",
        source = source,
        audioUrl = if (source == TrackSource.LOCAL) "content://media/external/audio/media/$index"
            else "https://example.invalid/$index.mp3"
    )

    @Test
    fun `catalog retains every local file even beyond the remote discovery cap`() {
        val local = (1..150).map { track(it, TrackSource.LOCAL) }
        val remote = (1..130).map { track(it, TrackSource.AUDIUS) }
        val pool = MusicRepository.mergeCandidatePool(local, remote)
        assertEquals(150, pool.count { it.source == TrackSource.LOCAL })
        assertEquals(100, pool.count { it.source == TrackSource.AUDIUS })
        assertTrue(pool.contains(local.last()))
    }

    @Test
    fun `merged catalog removes duplicates and blank audio URLs`() {
        val local = track(1, TrackSource.LOCAL)
        val remote = track(2, TrackSource.AUDIUS)
        val pool = MusicRepository.mergeCandidatePool(
            listOf(local, local, local.copy(id = "blank", audioUrl = "")),
            listOf(remote, remote)
        )
        assertEquals(listOf(local, remote), pool)
    }

    class AudioProvider : ContentProvider() {
        var queryCount = 0
        var lastSelection: String? = "not queried"
        var audioFile: File? = null

        override fun onCreate() = true
        override fun getType(uri: Uri) = "audio/wav"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(checkNotNull(audioFile), ParcelFileDescriptor.MODE_READ_ONLY)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queryCount++
            lastSelection = selection
            val columns = checkNotNull(projection)
            return MatrixCursor(columns).apply {
                for (id in 1L..5L) {
                    val values: Map<String, Any?> = mapOf(
                        MediaStore.Audio.Media._ID to id,
                        MediaStore.Audio.Media.TITLE to if (id == 3L) "" else "Audio $id",
                        MediaStore.Audio.Media.DISPLAY_NAME to "recording.m4a",
                        MediaStore.Audio.Media.ARTIST to "<unknown>",
                        MediaStore.Audio.Media.ALBUM to "Device",
                        MediaStore.Audio.Media.DURATION to if (id >= 3L) 0L else 1000L,
                        MediaStore.Audio.Media.SIZE to when (id) { 4L -> null; 5L -> 0L; else -> 512L }
                    )
                    addRow(columns.map { values[it] }.toTypedArray())
                }
            }
        }
    }
}
