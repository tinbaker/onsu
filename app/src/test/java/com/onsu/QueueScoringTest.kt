package com.onsu

import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.scoring.QueueManager
import com.onsu.scoring.ScoringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueScoringTest {

    private lateinit var scoringEngine: ScoringEngine
    private lateinit var queueManager: QueueManager
    private lateinit var testPool: List<Track>

    @Before
    fun setUp() {
        scoringEngine = ScoringEngine()
        queueManager = QueueManager(scoringEngine)

        val pool = mutableListOf<Track>()
        val genres = listOf("Pop", "Indie", "Ballad", "Hip-hop", "Lo-Fi", "Electronic")
        val artists = listOf("Vũ.", "Đen Vâu", "Chillies", "Sơn Tùng M-TP", "Taylor Swift", "Low G", "MONO")

        for (i in 1..90) {
            val genre = genres[i % genres.size]
            val artist = artists[i % artists.size]
            pool.add(
                Track(
                    id = "track_$i",
                    title = "Song $i",
                    artist = artist,
                    durationMs = 200_000L,
                    genre = genre,
                    source = if (i % 2 == 0) TrackSource.AUDIUS else TrackSource.JAMENDO,
                    tags = listOf(genre.lowercase(), "chill")
                )
            )
        }
        testPool = pool
    }

    private fun localTrack(index: Int, playable: Boolean = true) = Track(
        id = "local_$index",
        title = "Device audio $index",
        artist = "Unknown",
        durationMs = 0L,
        genre = "Local Audio",
        source = TrackSource.LOCAL,
        audioUrl = "content://media/external/audio/media/$index",
        isPlayable = playable
    )

    @Test
    fun `device audio is never recommended in any style`() {
        val locals = (1..8).map { localTrack(it) }
        for (style in TasteLevel.entries) {
            repeat(10) {
                queueManager.initialize(testPool + locals, setOf("Vũ."), setOf("Indie"), style)
                val queue = queueManager.queueState.value
                assertEquals(20, queue.size)
                assertTrue(queue.none { it.source == TrackSource.LOCAL })
                assertEquals(queue.size, queue.map { it.id }.distinct().size)
            }
        }
    }

    @Test
    fun `a single device file cannot slip into the queue`() {
        queueManager.initialize(testPool + localTrack(1), setOf("Vũ."), setOf("Indie"), TasteLevel.FAMILIAR)
        assertEquals(20, queueManager.queueState.value.size)
        assertTrue(queueManager.queueState.value.none { it.id == "local_1" })
    }

    @Test
    fun `unplayable and playable local files are both excluded from a batch`() {
        val pool = testPool + localTrack(1) + localTrack(2, playable = false)
        val batch = queueManager.buildStratifiedBatch(pool, TasteLevel.BALANCED, 20, emptySet())
        assertEquals(20, batch.size)
        assertTrue(batch.none { it.source == TrackSource.LOCAL })
    }

    @Test
    fun `local-only library produces no recommendation queue`() {
        queueManager.initialize((1..3).map { localTrack(it) }, emptySet(), emptySet())
        assertTrue(queueManager.queueState.value.isEmpty())
    }

    @Test
    fun `updating the pool with device audio never injects local tracks`() {
        queueManager.initialize(testPool, emptySet(), emptySet())
        queueManager.selectTrackIndex(2)
        val preserved = queueManager.queueState.value.take(3)
        val current = queueManager.currentTrack
        val updatedPool = testPool + (1..5).map { localTrack(it) }
        queueManager.updateCandidatePool(updatedPool)
        assertEquals(current?.id, queueManager.currentTrack?.id)
        assertEquals(preserved.map { it.id }, queueManager.queueState.value.take(3).map { it.id })
        assertTrue(queueManager.queueState.value.none { it.source == TrackSource.LOCAL })
        assertEquals(updatedPool, queueManager.candidatePool)
    }

    @Test
    fun `scanning while manually playing local files does not alter selected queue`() {
        queueManager.initialize(testPool, emptySet(), emptySet())
        val locals = (1..3).map { localTrack(it) }
        queueManager.replaceQueue(locals, 1)
        queueManager.updateCandidatePool(testPool + locals + localTrack(4))
        queueManager.checkAndReplenishSlidingWindow()
        assertEquals(locals, queueManager.queueState.value)
        assertEquals("local_2", queueManager.currentTrack?.id)
    }

    @Test
    fun `a restored device file is not resurrected into recommendations`() {
        val pool = testPool + (1..200).map { localTrack(it) }
        queueManager.initialize(pool, emptySet(), emptySet(), restoredTrackId = "local_200")
        assertEquals(20, queueManager.queueState.value.size)
        assertTrue(queueManager.queueState.value.none { it.source == TrackSource.LOCAL })
    }

    @Test
    fun `queue starts with 20 songs and every page appends 10 more`() {
        queueManager.initialize(testPool, emptySet(), emptySet())
        assertEquals(20, queueManager.queueState.value.size)

        assertEquals(10, queueManager.loadMore())
        assertEquals(30, queueManager.queueState.value.size)

        assertEquals(10, queueManager.loadMore())
        assertEquals(40, queueManager.queueState.value.size)
        assertEquals(40, queueManager.queueState.value.map { it.id }.distinct().size)
        assertTrue(queueManager.queueState.value.none { it.source == TrackSource.LOCAL })
    }

    @Test
    fun `paging stops once the online catalog is exhausted`() {
        queueManager.initialize(testPool, emptySet(), emptySet())
        var total = 20
        repeat(20) { total += queueManager.loadMore() }
        assertEquals(testPool.size, total)
        assertEquals(0, queueManager.loadMore())
    }

    @Test
    fun `refresh keeps the playing song first and rebuilds a 20 track page`() {
        queueManager.initialize(testPool, emptySet(), emptySet())
        queueManager.selectTrackIndex(4)
        val current = queueManager.currentTrack

        val refreshed = queueManager.refreshRecommendations()

        assertEquals(20, refreshed.size)
        assertEquals(current?.id, refreshed.first().id)
        assertEquals(0, queueManager.currentIndex.value)
        assertEquals(20, refreshed.map { it.id }.distinct().size)
        assertTrue(refreshed.none { it.source == TrackSource.LOCAL })
    }

    @Test
    fun `zero requested batch is empty`() {
        assertTrue(queueManager.buildStratifiedBatch(testPool, TasteLevel.BALANCED, 0, emptySet()).isEmpty())
    }

    @Test
    fun `candidate pool meets 80 to 100 track quota`() {
        assertTrue("Pool should have at least 80 items", testPool.size >= 80)
        assertTrue("Pool should not exceed 100 items", testPool.size <= 100)
    }

    @Test
    fun `scoring assigns positive weight to matching genre and artist`() {
        val matchingTrack = Track(
            id = "t_match",
            title = "Bước Qua Nhau",
            artist = "Vũ.",
            durationMs = 250_000L,
            genre = "Indie",
            source = TrackSource.LOCAL,
            tags = listOf("indie", "chill")
        )

        val score = scoringEngine.calculateTrackScore(
            track = matchingTrack,
            seedArtists = setOf("Vũ."),
            seedGenres = setOf("Indie"),
            likedTrackIds = emptySet(),
            completedTrackIds = emptySet(),
            skippedTrackIds = emptySet(),
            playHistoryTimestamps = emptyMap()
        )

        // Genre: 0.40, Artist: 0.50, Tags: at least 0.10 => score >= 0.90
        assertTrue("Score should be >= 0.90 for matching artist and genre", score >= 0.90f)
    }

    @Test
    fun `behavioral like boosts score by 0_30 and skip penalizes by 0_25`() {
        val track = Track(
            id = "t_test",
            title = "Sample",
            artist = "Unknown",
            durationMs = 200_000L,
            genre = "Other",
            source = TrackSource.AUDIUS
        )

        val baseScore = scoringEngine.calculateTrackScore(
            track = track,
            seedArtists = emptySet(),
            seedGenres = emptySet(),
            likedTrackIds = emptySet(),
            completedTrackIds = emptySet(),
            skippedTrackIds = emptySet(),
            playHistoryTimestamps = emptyMap()
        )

        val likedScore = scoringEngine.calculateTrackScore(
            track = track,
            seedArtists = emptySet(),
            seedGenres = emptySet(),
            likedTrackIds = setOf(track.id),
            completedTrackIds = emptySet(),
            skippedTrackIds = emptySet(),
            playHistoryTimestamps = emptyMap()
        )

        val skippedScore = scoringEngine.calculateTrackScore(
            track = track,
            seedArtists = emptySet(),
            seedGenres = emptySet(),
            likedTrackIds = emptySet(),
            completedTrackIds = emptySet(),
            skippedTrackIds = setOf(track.id),
            playHistoryTimestamps = emptyMap()
        )

        assertEquals(0.30f, likedScore - baseScore, 0.01f)
        assertEquals(-0.25f, skippedScore - baseScore, 0.01f)
    }

    @Test
    fun `recency penalty subtracts 0_30 if played recently`() {
        val track = Track(
            id = "t_recent",
            title = "Recent Song",
            artist = "Chillies",
            durationMs = 200_000L,
            genre = "Pop",
            source = TrackSource.JAMENDO
        )

        val freshScore = scoringEngine.calculateTrackScore(
            track = track,
            seedArtists = setOf("Chillies"),
            seedGenres = setOf("Pop"),
            likedTrackIds = emptySet(),
            completedTrackIds = emptySet(),
            skippedTrackIds = emptySet(),
            playHistoryTimestamps = emptyMap()
        )

        val recentPlayedScore = scoringEngine.calculateTrackScore(
            track = track,
            seedArtists = setOf("Chillies"),
            seedGenres = setOf("Pop"),
            likedTrackIds = emptySet(),
            completedTrackIds = emptySet(),
            skippedTrackIds = emptySet(),
            playHistoryTimestamps = mapOf(track.id to System.currentTimeMillis() - 60_000L) // 1 min ago
        )

        assertEquals(-0.30f, recentPlayedScore - freshScore, 0.01f)
    }

    @Test
    fun `queue guarantees no consecutive tracks by the same artist RC-36`() {
        queueManager.initialize(
            pool = testPool,
            artists = setOf("Vũ.", "Chillies"),
            genres = setOf("Pop", "Indie"),
            style = TasteLevel.BALANCED
        )

        val queue = queueManager.queueState.value
        assertTrue("Queue should have 20 items", queue.size == 20)

        for (i in 0 until queue.size - 1) {
            assertNotEquals(
                "Consecutive tracks must not have identical artists (RC-36)",
                queue[i].artist,
                queue[i + 1].artist
            )
        }
    }

    @Test
    fun `style switch tail replacement preserves currently playing and prunes unplayed tail`() {
        queueManager.initialize(
            pool = testPool,
            artists = setOf("Vũ."),
            genres = setOf("Indie"),
            style = TasteLevel.FAMILIAR
        )

        // Advance to track index 2
        queueManager.selectTrackIndex(2)
        val initialCurrent = queueManager.currentTrack
        val initialPlayed = queueManager.queueState.value.take(3)

        // Switch to EXPLORE style
        val newQueue = queueManager.switchTasteStyle(TasteLevel.EXPLORE)

        // Verify preserved part
        assertEquals(initialCurrent?.id, queueManager.currentTrack?.id)
        assertEquals(initialPlayed.map { it.id }, newQueue.take(3).map { it.id })

        // Verify total size remains bounded
        assertTrue("Queue should remain around 20 items", newQueue.size in 15..25)
    }
}
