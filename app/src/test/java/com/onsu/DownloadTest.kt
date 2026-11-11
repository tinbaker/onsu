package com.onsu

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.onsu.data.download.AudioFetcher
import com.onsu.data.download.DownloadException
import com.onsu.data.download.DownloadOutcome
import com.onsu.data.download.DownloadPublisher
import com.onsu.data.download.DownloadStore
import com.onsu.data.download.PublishRequest
import com.onsu.data.download.PublishedTrack
import com.onsu.data.download.TrackDownloader
import com.onsu.data.local.AppDatabase
import com.onsu.data.local.entities.DownloadedTrackEntity
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.data.repository.MusicRepository
import com.onsu.player.MediaItemBuilder
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tải nhạc online về máy.
 *
 * Bộ test khoá lại bốn hành vi quan trọng: tệp tải xong phải được đưa sang thư mục nhạc
 * dùng chung (không nằm trong bộ nhớ riêng của app), tệp phải được đăng ký để phát
 * ngoại tuyến, một phản hồi lỗi của máy chủ không được tạo ra "bài đã tải" giả, và bản
 * ghi Room phải biến mất khi tệp trên máy không còn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadTest {

    private lateinit var context: Context
    private lateinit var publisher: FakePublisher
    private val audioBytes = ByteArray(4096) { 0x41 }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        publisher = FakePublisher(context)
        publisher.clear()
        DownloadStore.clear()
        DownloadStore.cacheDirectory(context).listFiles()?.forEach { it.delete() }
        // Room giữ một instance duy nhất trong tiến trình, nên bảng tải nhạc còn dữ liệu
        // của test chạy trước — phải dọn để mỗi test bắt đầu từ trạng thái trống.
        val dao = AppDatabase.getInstance(context).downloadDao()
        runBlocking { dao.getAllDownloadsOnce().forEach { dao.deleteDownload(it.trackId) } }
    }

    @Test
    fun `download stores the audio file and registers it for offline playback`() = runTest {
        val track = track("aud_dl_ok")
        val downloader = TrackDownloader(context, FakeFetcher(audioBytes, "audio/mpeg"), publisher)
        var lastProgress = 0f

        val outcome = downloader.download(track) { lastProgress = it }

        assertTrue(outcome is DownloadOutcome.Success)
        val success = outcome as DownloadOutcome.Success
        val file = File(requireNotNull(success.uri.path))
        assertTrue(file.exists())
        assertEquals(audioBytes.size.toLong(), file.length())
        assertEquals(1f, lastProgress, 0f)
        assertEquals("Artist aud_dl_ok - Song aud_dl_ok.mp3", success.displayName)
        assertEquals(success.uri, DownloadStore.uriFor(track.id))
        // Tệp tạm phải được dọn sau khi đưa sang thư mục nhạc.
        assertTrue(DownloadStore.cacheDirectory(context).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `server error payload is rejected and leaves no downloaded file`() = runTest {
        val track = track("aud_dl_json_error")
        val body = """{"error":"track not found"}""".toByteArray()
        val downloader = TrackDownloader(context, FakeFetcher(body, "application/json"), publisher)

        val outcome = downloader.download(track)

        assertTrue(outcome is DownloadOutcome.Failure)
        assertNull(DownloadStore.uriFor(track.id))
        assertTrue(publisher.files().isEmpty())
        assertTrue(DownloadStore.cacheDirectory(context).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `network failure is reported with a readable message`() = runTest {
        val track = track("aud_dl_fail")
        val downloader = TrackDownloader(
            context,
            FakeFetcher(audioBytes, failWith = DownloadException("Máy chủ nhạc trả về lỗi 503. Thử lại sau.")),
            publisher
        )

        val outcome = downloader.download(track)

        assertTrue(outcome is DownloadOutcome.Failure)
        assertEquals("Máy chủ nhạc trả về lỗi 503. Thử lại sau.", (outcome as DownloadOutcome.Failure).message)
        assertTrue(publisher.files().isEmpty())
    }

    @Test
    fun `downloaded track is persisted and disappears when the file is gone`() = runTest {
        val track = track("aud_dl_persist")
        val repository = repository()

        val outcome = repository.downloadTrack(track)
        assertTrue(outcome is DownloadOutcome.Success)

        val stored = repository.downloadedTracksFlow.first()
        assertEquals(1, stored.size)
        val storedTrack = stored.first()
        assertEquals(track.id, storedTrack.id)
        assertTrue(storedTrack.isDownloaded)
        assertEquals(audioBytes.size.toLong(), storedTrack.fileSizeBytes)
        assertNotNull(DownloadStore.uriFor(track.id))

        // Người dùng dọn bộ nhớ / xoá tệp bằng ứng dụng khác: bản ghi phải tự biến mất.
        File(requireNotNull((outcome as DownloadOutcome.Success).uri.path)).delete()
        repository.refreshDownloadRegistry()
        assertTrue(repository.downloadedTracksFlow.first().isEmpty())
        assertNull(DownloadStore.uriFor(track.id))
    }

    @Test
    fun `deleting a download removes both the file and the record`() = runTest {
        val track = track("aud_dl_delete")
        val repository = repository()
        val outcome = repository.downloadTrack(track)
        val uri = (outcome as DownloadOutcome.Success).uri

        assertTrue(repository.deleteDownload(track.id))

        assertFalse(File(requireNotNull(uri.path)).exists())
        assertTrue(repository.downloadedTracksFlow.first().isEmpty())
        assertNull(DownloadStore.uriFor(track.id))
        assertFalse(repository.deleteDownload(track.id))
    }

    /**
     * Bản ghi tạo từ v3 trỏ tới tệp trong bộ nhớ riêng của app. Lần mở app đầu tiên sau
     * khi cập nhật phải chuyển tệp đó sang thư mục nhạc của máy, nếu không người dùng
     * vẫn không thấy bài đã tải đâu.
     */
    @Test
    fun `download from the previous version is moved into the music folder`() = runTest {
        val track = track("aud_dl_legacy")
        val legacy = DownloadStore.legacyFile(context, "legacy.mp3")!!
        legacy.parentFile?.mkdirs()
        legacy.writeBytes(audioBytes)

        val dao = AppDatabase.getInstance(context).downloadDao()
        dao.saveDownload(
            DownloadedTrackEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = null,
                streamUrl = track.audioUrl,
                source = track.source.name,
                sourceId = track.sourceId,
                durationMs = track.durationMs,
                genre = track.genre,
                fileName = "legacy.mp3",
                fileSizeBytes = audioBytes.size.toLong()
            )
        )

        val repository = repository()
        repository.refreshDownloadRegistry()

        val uri = DownloadStore.uriFor(track.id)
        assertNotNull(uri)
        assertTrue(requireNotNull(uri).toString().startsWith("file://"))
        assertFalse("Tệp cũ phải được dời đi, không để lại bản trùng", legacy.exists())
        assertEquals(uri.toString(), dao.getDownload(track.id)?.contentUri)
    }

    @Test
    fun `device audio is never downloadable`() = runTest {
        val localTrack = track("local_42").copy(source = TrackSource.LOCAL)
        val downloader = TrackDownloader(context, FakeFetcher(audioBytes, "audio/mpeg"), publisher)

        val outcome = downloader.download(localTrack)

        assertTrue(outcome is DownloadOutcome.Failure)
        assertTrue(publisher.files().isEmpty())
    }

    @Test
    fun `player uses the downloaded file when the track is available offline`() {
        val track = track("aud_dl_media_item")
        val localUri = Uri.parse("content://media/external/audio/media/42")

        val onlineItem = MediaItemBuilder.fromTrack(track)
        assertEquals(track.audioUrl, onlineItem.localConfiguration?.uri?.toString())

        val offlineItem = MediaItemBuilder.fromTrack(track, localUri)
        assertEquals(localUri, offlineItem.localConfiguration?.uri)
        assertEquals(track.id, offlineItem.mediaId)
    }

    @Test
    fun `file names are safe and extensions follow the content type`() {
        val sanitized = DownloadStore.sanitize("../../etc/passwd")
        assertFalse(sanitized.contains('/'))
        assertFalse(sanitized.contains(".."))
        assertTrue(sanitized.isNotBlank())

        // Tên tệp giữ dấu tiếng Việt nhưng không được chứa ký tự hệ thống tệp cấm.
        val named = DownloadStore.fileName("Nguyễn Văn A", "Bài: Hát/Live?", "mp3")
        assertEquals("Nguyễn Văn A - Bài_ Hát_Live_.mp3", named)

        assertEquals("mp3", TrackDownloader.extensionFor("audio/mpeg", "https://x/y"))
        assertEquals("m4a", TrackDownloader.extensionFor("audio/mp4", "https://x/y"))
        assertEquals("ogg", TrackDownloader.extensionFor("audio/ogg", "https://x/y"))
        assertEquals("wav", TrackDownloader.extensionFor("audio/wav", "https://x/y"))
        // Máy chủ không công bố content-type: lấy đuôi từ URL.
        assertEquals("m4a", TrackDownloader.extensionFor(null, "https://x/y/track.m4a?token=1"))
        assertEquals("mp3", TrackDownloader.extensionFor(null, "https://x/y/stream"))

        assertEquals("audio/mpeg", TrackDownloader.mimeTypeFor("mp3"))
        assertEquals("audio/mp4", TrackDownloader.mimeTypeFor("m4a"))
        assertEquals("audio/ogg", TrackDownloader.mimeTypeFor("ogg"))
    }

    /**
     * Khi máy chủ không trả về bài nào (đang offline), kho nhạc dự phòng không được lấp
     * vào: đó chính là những "đề xuất bừa bãi" mà người dùng thấy khi mất mạng.
     */
    @Test
    fun `empty catalog stays empty instead of using the fallback pool`() = runTest {
        assertTrue(repository().loadRecommendationPool().isEmpty())
    }

    private fun repository() = MusicRepository(
        context,
        fetchRemoteCatalog = { emptyList() },
        publisher = publisher,
        downloader = TrackDownloader(context, FakeFetcher(audioBytes, "audio/mpeg"), publisher)
    )

    private fun track(id: String) = Track(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        durationMs = 180_000L,
        genre = "Pop",
        source = TrackSource.AUDIUS,
        sourceId = id.removePrefix("aud_"),
        audioUrl = "https://example.invalid/$id.mp3"
    )

    private class FakeFetcher(
        private val body: ByteArray,
        private val contentType: String? = "audio/mpeg",
        private val failWith: DownloadException? = null
    ) : AudioFetcher {
        override suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit): String? {
            failWith?.let { throw it }
            target.parentFile?.mkdirs()
            target.writeBytes(body)
            onProgress(1f)
            return contentType
        }
    }

    /** Thay cho MediaStore: ghi tệp ra một thư mục "công khai" giả trong bộ nhớ test. */
    private class FakePublisher(context: Context) : DownloadPublisher {
        private val directory = File(context.filesDir, "fake_music").apply { mkdirs() }

        fun files(): List<File> = directory.listFiles().orEmpty().toList()

        fun clear() {
            directory.listFiles()?.forEach { it.delete() }
        }

        override suspend fun publish(tempFile: File, request: PublishRequest): PublishedTrack {
            val target = File(directory, request.displayName)
            tempFile.copyTo(target, overwrite = true)
            return PublishedTrack(Uri.fromFile(target), request.displayName, target.length())
        }

        override suspend fun exists(uri: Uri): Boolean {
            val file = uri.path?.let(::File) ?: return false
            return file.exists() && file.length() > 0L
        }

        override suspend fun delete(uri: Uri): Boolean {
            val file = uri.path?.let(::File) ?: return false
            return !file.exists() || file.delete()
        }
    }
}
