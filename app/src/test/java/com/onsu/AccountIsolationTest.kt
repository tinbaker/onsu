package com.onsu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onsu.data.models.PlaybackSession
import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.data.models.UserProfile
import com.onsu.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Thư viện phải thuộc về tài khoản, không thuộc về thiết bị.
 *
 * Trước đây `liked_tracks`/`playlists` không có cột chủ sở hữu nên đổi tài khoản vẫn
 * thấy chung một thư viện. Bộ test này khoá lại hành vi đúng: mỗi `ownerId` chỉ thấy
 * dữ liệu của chính mình, và guest tách khỏi mọi tài khoản Google.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountIsolationTest {

    private lateinit var repository: MusicRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        repository = MusicRepository(context, fetchRemoteCatalog = { emptyList() })
    }

    @Test
    fun `liked tracks and playlists are scoped to the signed-in account`() = runTest {
        val stamp = System.nanoTime()
        val accountA = profile("acc_a_$stamp")
        val accountB = profile("acc_b_$stamp")
        val trackId = "track_$stamp"

        repository.saveUserProfile(accountA)
        repository.saveLikedTrack(track(trackId))
        val playlistId = repository.createPlaylist("Playlist A")
        repository.addTrackToPlaylist(playlistId, track(trackId))

        assertEquals(1, repository.likedTracksFlow.first().size)
        assertEquals(1, repository.playlistsFlow.first().size)
        assertEquals(1, repository.getTracksForPlaylist(playlistId).first().size)

        // Đăng nhập tài khoản khác: thư viện phải rỗng.
        repository.saveUserProfile(accountB)
        assertTrue(repository.likedTracksFlow.first().isEmpty())
        assertTrue(repository.playlistsFlow.first().isEmpty())
        // Biết id playlist của A cũng không đọc được bài hát.
        assertTrue(repository.getTracksForPlaylist(playlistId).first().isEmpty())

        // Quay lại tài khoản A: dữ liệu còn nguyên.
        repository.saveUserProfile(accountA)
        assertEquals(1, repository.likedTracksFlow.first().size)
        assertEquals(1, repository.playlistsFlow.first().size)
        assertEquals(1, repository.getTracksForPlaylist(playlistId).first().size)
    }

    @Test
    fun `taste seeds and playback session do not leak between accounts`() = runTest {
        val stamp = System.nanoTime()
        val accountA = profile("acc_taste_a_$stamp")
        val accountB = profile("acc_taste_b_$stamp")
        val trackId = "session_$stamp"

        repository.saveUserProfile(accountA)
        repository.saveTasteSeeds(setOf("Vũ."), setOf("Indie"))
        repository.savePlaybackSession(session(trackId))

        repository.saveUserProfile(accountB)
        assertEquals(Pair(emptySet<String>(), emptySet<String>()), repository.getTasteSeeds())
        assertNull(repository.restorePlaybackSession())

        repository.saveUserProfile(accountA)
        assertEquals(setOf("Vũ."), repository.getTasteSeeds().first)
        assertEquals(setOf("Indie"), repository.getTasteSeeds().second)
        assertEquals(trackId, repository.restorePlaybackSession()?.trackId)
    }

    @Test
    fun `guest library is separate from a signed-in account`() = runTest {
        val stamp = System.nanoTime()
        val account = profile("acc_guest_$stamp")
        val trackId = "guest_$stamp"

        repository.saveUserProfile(account)
        repository.saveLikedTrack(track(trackId))
        assertTrue(repository.likedTracksFlow.first().any { it.id == trackId })

        // Đăng xuất = quay về thư viện khách, không thấy dữ liệu của tài khoản vừa rồi.
        repository.clearUser()
        assertTrue(repository.likedTracksFlow.first().none { it.id == trackId })

        repository.saveUserProfile(account)
        assertTrue(repository.likedTracksFlow.first().any { it.id == trackId })
    }

    private fun profile(id: String) = UserProfile(
        id = id,
        email = "$id@example.com",
        displayName = "User $id",
        photoUrl = null,
        isLoggedIn = true,
        isGuest = false
    )

    private fun track(id: String) = Track(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        durationMs = 180_000L,
        genre = "Pop",
        source = TrackSource.AUDIUS,
        audioUrl = "https://example.invalid/$id.mp3"
    )

    private fun session(trackId: String) = PlaybackSession(
        trackId = trackId,
        title = "Song $trackId",
        artist = "Artist $trackId",
        album = "Album",
        artworkUrl = null,
        streamUrl = "https://example.invalid/$trackId.mp3",
        source = TrackSource.AUDIUS,
        sourceId = trackId,
        durationMs = 180_000L,
        genre = "Pop",
        positionMs = 0L,
        playlistMode = TasteLevel.BALANCED
    )
}
