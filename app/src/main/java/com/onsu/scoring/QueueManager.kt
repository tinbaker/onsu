package com.onsu.scoring

import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QueueManager(
    private val scoringEngine: ScoringEngine = ScoringEngine()
) {
    private val _queueState = MutableStateFlow<List<Track>>(emptyList())
    val queueState: StateFlow<List<Track>> = _queueState.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _activeStyle = MutableStateFlow(TasteLevel.BALANCED)
    val activeStyle: StateFlow<TasteLevel> = _activeStyle.asStateFlow()

    var candidatePool: List<Track> = emptyList()
        private set
    private var isRecommendationQueue = false

    /** Refresh the online catalog after a reload; a manual playlist is left alone. */
    fun updateCandidatePool(pool: List<Track>) {
        candidatePool = pool
        if (isRecommendationQueue) switchTasteStyle(_activeStyle.value)
    }

    val seedArtists = mutableSetOf<String>()
    val seedGenres = mutableSetOf<String>()
    val likedTrackIds = mutableSetOf<String>()
    val completedTrackIds = mutableSetOf<String>()
    val skippedTrackIds = mutableSetOf<String>()
    val playHistoryTimestamps = mutableMapOf<String, Long>()

    val currentTrack: Track?
        get() {
            val q = _queueState.value
            val idx = _currentIndex.value
            return if (idx in q.indices) q[idx] else null
        }

    fun initialize(
        pool: List<Track>,
        artists: Set<String>,
        genres: Set<String>,
        style: TasteLevel = TasteLevel.BALANCED,
        restoredTrackId: String? = null
    ) {
        candidatePool = pool
        isRecommendationQueue = true
        seedArtists.clear()
        seedArtists.addAll(artists)
        seedGenres.clear()
        seedGenres.addAll(genres)
        _activeStyle.value = style

        val sampled = buildStratifiedBatch(
            pool = candidatePool,
            style = style,
            targetCount = INITIAL_PAGE_SIZE,
            excludeIds = emptySet()
        )
        // A resumed session may point at a device file that is no longer part of the
        // online catalog. Device audio is never recommended, so in that case we simply
        // start the fresh batch instead of resurrecting a local file into the queue.
        val restored = candidatePool.firstOrNull {
            it.id == restoredTrackId && it.isPlayable && it.source != TrackSource.LOCAL
        }
        val initialQueue = if (restored != null && sampled.none { it.id == restored.id }) {
            listOf(restored.copy(isLiked = restored.id in likedTrackIds)) +
                sampled.take(INITIAL_PAGE_SIZE - 1)
        } else sampled

        _queueState.value = initialQueue
        _currentIndex.value = 0

        if (restoredTrackId != null) {
            val idx = initialQueue.indexOfFirst { it.id == restoredTrackId }
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        }
    }

    /**
     * Immediate Style-Switching via Queue Tail Replacement:
     * Preserves currently playing track and already-played history.
     * Prunes unplayed future tail.
     * Rebuilds upcoming tail immediately with new style quota so next song is audibly shifted!
     */
    fun switchTasteStyle(newStyle: TasteLevel): List<Track> {
        isRecommendationQueue = true
        _activeStyle.value = newStyle
        val currentQueue = _queueState.value
        val currentIdx = _currentIndex.value

        if (currentQueue.isEmpty()) {
            val fresh = buildStratifiedBatch(candidatePool, newStyle, INITIAL_PAGE_SIZE, emptySet())
            _queueState.value = fresh
            return fresh
        }

        // 1. Preserve played items and current playing track
        val preservedPart = currentQueue.take(currentIdx + 1)
        val excludeIds = preservedPart.map { it.id }.toSet()

        // 2. Rebuild unplayed tail according to new quota
        val neededNew = (INITIAL_PAGE_SIZE - preservedPart.size).coerceAtLeast(PAGE_SIZE)
        val newTail = buildStratifiedBatch(candidatePool, newStyle, neededNew, excludeIds)

        // 3. Assemble new queue
        val assembledQueue = preservedPart + newTail
        _queueState.value = assembledQueue
        return assembledQueue
    }

    /**
     * Regenerates the recommendation list (pull-to-refresh on the main screen).
     *
     * The song that is currently playing is kept as the first item so a refresh never
     * stops or restarts the audio: the player only sees its head trimmed and its tail
     * replaced, which keeps the same media item playing at the same position.
     */
    fun refreshRecommendations(): List<Track> {
        isRecommendationQueue = true
        val current = currentTrack
        val excludeIds = current?.let { setOf(it.id) } ?: emptySet()
        val batch = buildStratifiedBatch(
            pool = candidatePool,
            style = _activeStyle.value,
            targetCount = INITIAL_PAGE_SIZE,
            excludeIds = excludeIds
        )

        if (batch.isEmpty()) return _queueState.value

        val refreshed = if (current != null) {
            listOf(current.copy(isLiked = current.id in likedTrackIds)) +
                batch.take(INITIAL_PAGE_SIZE - 1)
        } else {
            batch
        }
        _queueState.value = refreshed
        _currentIndex.value = 0
        return refreshed
    }

    /**
     * Appends one more page of recommendations when the user reaches the end of the
     * list. Returns how many tracks were actually added (0 means the catalog has been
     * exhausted, so the UI can stop asking for more).
     */
    fun loadMore(count: Int = PAGE_SIZE): Int {
        if (!isRecommendationQueue) return 0
        val q = _queueState.value
        if (q.isEmpty()) return 0

        val excludeIds = q.mapTo(HashSet()) { it.id }
        val batch = buildStratifiedBatch(candidatePool, _activeStyle.value, count, excludeIds)
        if (batch.isEmpty()) return 0

        _queueState.value = q + batch
        return batch.size
    }

    fun moveToNextTrack(): Track? {
        val q = _queueState.value
        val cur = _currentIndex.value

        // Record play history for previous track
        if (cur in q.indices) {
            val currentId = q[cur].id
            playHistoryTimestamps[currentId] = System.currentTimeMillis()
        }

        if (cur < q.size - 1) {
            _currentIndex.value = cur + 1
            checkAndReplenishSlidingWindow()
            return _queueState.value[_currentIndex.value]
        }
        return null
    }

    fun moveToPreviousTrack(): Track? {
        val cur = _currentIndex.value
        if (cur > 0) {
            _currentIndex.value = cur - 1
            return _queueState.value[_currentIndex.value]
        }
        return currentTrack
    }

    fun selectTrackIndex(index: Int): Track? {
        val q = _queueState.value
        if (index in q.indices) {
            _currentIndex.value = index
            checkAndReplenishSlidingWindow()
            return q[index]
        }
        return null
    }

    /**
     * Thay toàn bộ queue — dùng khi người dùng phát một playlist hoặc một bài lẻ từ thư viện.
     *
     * Phải đi qua đây thay vì set thẳng vào player, nếu không queue và player sẽ lệch nhau
     * ngay từ bài đầu tiên.
     */
    fun replaceQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        isRecommendationQueue = false
        _queueState.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, tracks.lastIndex)
    }

    /**
     * Xoá sạch hàng đợi khi đổi tài khoản.
     *
     * Đặt `isRecommendationQueue = false` để lần nạp pool kế tiếp không dựng lại hàng
     * đợi cũ; [initialize] sẽ bật lại cờ này cho tài khoản mới.
     */
    fun clearQueue() {
        isRecommendationQueue = false
        _queueState.value = emptyList()
        _currentIndex.value = 0
    }

    fun recordComplete(trackId: String) {
        completedTrackIds.add(trackId)
        skippedTrackIds.remove(trackId)
    }

    fun recordSkip(trackId: String) {
        skippedTrackIds.add(trackId)
        completedTrackIds.remove(trackId)
    }

    fun recordLike(trackId: String, isLiked: Boolean) {
        if (isLiked) {
            likedTrackIds.add(trackId)
        } else {
            likedTrackIds.remove(trackId)
        }
        _queueState.value = _queueState.value.map {
            if (it.id == trackId) it.copy(isLiked = isLiked) else it
        }
    }

    fun syncLikedTracks(likedIds: Set<String>) {
        likedTrackIds.clear()
        likedTrackIds.addAll(likedIds)
        _queueState.value = _queueState.value.map {
            it.copy(isLiked = likedIds.contains(it.id))
        }
    }

    /**
     * Playback safety net: when fewer than [UPCOMING_BUFFER] songs are left, append a
     * page so continuous playback never hits the end of the list.
     *
     * Played items are no longer trimmed from the head. The main screen is now a
     * scrollable list the user navigates by hand, and dropping the head would yank the
     * scroll position while they read it.
     */
    fun checkAndReplenishSlidingWindow() {
        if (!isRecommendationQueue) return
        val q = _queueState.value
        val cur = _currentIndex.value
        if (q.isEmpty() || cur !in q.indices) return

        val unplayedCount = q.size - 1 - cur
        if (unplayedCount > UPCOMING_BUFFER) return

        val excludeIds = q.mapTo(HashSet()) { it.id }
        val newBatch = buildStratifiedBatch(candidatePool, _activeStyle.value, PAGE_SIZE, excludeIds)
        if (newBatch.isNotEmpty()) _queueState.value = q + newBatch
    }

    /**
     * 3-Tier Stratification & Sampling:
     * Tier 1 (Quen thuộc): Top 30%
     * Tier 2 (Vừa): Middle 40%
     * Tier 3 (Mới lạ): Bottom 30%
     * Quotas:
     * - FAMILIAR: 70% T1 (14) + 20% T2 (4) + 10% T3 (2)
     * - BALANCED: 40% T1 (8) + 40% T2 (8) + 20% T3 (4)
     * - EXPLORE:  20% T1 (4) + 30% T2 (6) + 50% T3 (10)
     *
     * Device audio is filtered out before scoring: the automatic playlist is an online
     * discovery feature, and files stored on the phone must never be recommended.
     * They stay reachable through the local library screen only.
     */
    fun buildStratifiedBatch(
        pool: List<Track>,
        style: TasteLevel,
        targetCount: Int,
        excludeIds: Set<String>
    ): List<Track> {
        if (targetCount <= 0) return emptyList()
        val candidates = pool.filter {
            it.isPlayable && it.source != TrackSource.LOCAL && !excludeIds.contains(it.id)
        }.distinctBy { it.id }
        if (candidates.isEmpty()) return emptyList()

        // 1. Score all candidates
        val scored = candidates.map { track ->
            val score = scoringEngine.calculateTrackScore(
                track = track,
                seedArtists = seedArtists,
                seedGenres = seedGenres,
                likedTrackIds = likedTrackIds,
                completedTrackIds = completedTrackIds,
                skippedTrackIds = skippedTrackIds,
                playHistoryTimestamps = playHistoryTimestamps
            )
            Pair(track, score)
        }.sortedByDescending { it.second }

        // Filter out extreme negative outliers
        val valid = scored.filter { it.second > -0.50f }.map { it.first }
        if (valid.isEmpty()) return candidates.take(targetCount)

        // 2. Divide into 3 tiers
        val size = valid.size
        val tier1Boundary = (size * 0.30f).toInt().coerceAtLeast(1)
        val tier2Boundary = (size * 0.70f).toInt().coerceAtLeast(tier1Boundary + 1).coerceAtMost(size)

        val tier1 = valid.subList(0, tier1Boundary)
        val tier2 = valid.subList(tier1Boundary, tier2Boundary)
        val tier3 = valid.subList(tier2Boundary, size)

        // 3. Compute quotas
        val countT1: Int
        val countT2: Int
        val countT3: Int

        when (style) {
            TasteLevel.FAMILIAR -> {
                countT1 = (targetCount * 0.70f).toInt()
                countT2 = (targetCount * 0.20f).toInt()
                countT3 = targetCount - countT1 - countT2
            }
            TasteLevel.BALANCED -> {
                countT1 = (targetCount * 0.40f).toInt()
                countT2 = (targetCount * 0.40f).toInt()
                countT3 = targetCount - countT1 - countT2
            }
            TasteLevel.EXPLORE -> {
                countT1 = (targetCount * 0.20f).toInt()
                countT2 = (targetCount * 0.30f).toInt()
                countT3 = targetCount - countT1 - countT2
            }
        }

        val sampled = mutableListOf<Track>()
        sampled.addAll(tier1.shuffled().take(countT1))
        sampled.addAll(tier2.shuffled().take(countT2))
        sampled.addAll(tier3.shuffled().take(countT3))

        // Fallback if tiers were smaller than quota
        if (sampled.size < targetCount) {
            val remaining = valid.filterNot { sampled.contains(it) }
            sampled.addAll(remaining.take(targetCount - sampled.size))
        }

        // 4. Ensure no consecutive repeat artists (RC-36)
        val result = mutableListOf<Track>()
        val pending = sampled.toMutableList()

        while (pending.isNotEmpty()) {
            val lastArtist = result.lastOrNull()?.artist
            // Choose the largest remaining artist group first; choosing the
            // first different artist can exhaust separators and strand repeats.
            val nextTrack = pending.groupBy { it.artist }
                .filterKeys { it != lastArtist }
                .maxByOrNull { it.value.size }?.value?.first() ?: pending.first()
            result.add(nextTrack)
            pending.remove(nextTrack)
        }

        return result.map { it.copy(isLiked = likedTrackIds.contains(it.id)) }
    }

    companion object {
        /** First page shown when the app opens or the taste style changes. */
        const val INITIAL_PAGE_SIZE = 20

        /** Every scroll-to-bottom load appends this many songs. */
        const val PAGE_SIZE = 10

        /** Append a page while playing when this few songs remain ahead. */
        private const val UPCOMING_BUFFER = 3
    }
}
