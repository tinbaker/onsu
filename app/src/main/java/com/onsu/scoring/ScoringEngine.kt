package com.onsu.scoring

import com.onsu.data.models.Track

/**
 * ScoringEngine: Grounded 7-Feature Taste Recommendation Algorithm
 * 1. Thể loại (Genre): +0.40 match
 * 2. Thẻ tự do (Tags): +0.10 each (up to +0.30)
 * 3. Nghệ sĩ (Artist): +0.50 match
 * 4. Nghe hết (Complete > 80%): +0.08
 * 5. Thả tim (Like): +0.30
 * 6. Bỏ qua sớm (Skip < 30s): -0.25
 * 7. Phạt nghe gần đây (Recency penalty): -0.30 if played < 24h
 */
class ScoringEngine {

    fun calculateTrackScore(
        track: Track,
        seedArtists: Set<String>,
        seedGenres: Set<String>,
        likedTrackIds: Set<String>,
        completedTrackIds: Set<String>,
        skippedTrackIds: Set<String>,
        playHistoryTimestamps: Map<String, Long> // trackId -> timestamp
    ): Float {
        if (!track.isPlayable) return -100f

        var score = 0f

        // 1. Thể loại (Genre)
        if (seedGenres.any { it.equals(track.genre, ignoreCase = true) }) {
            score += 0.40f
        }

        // 2. Thẻ tự do (Tags / Vartags)
        val matchingTagsCount = track.tags.count { tag ->
            seedGenres.any { it.contains(tag, ignoreCase = true) } || tag.equals("chill", ignoreCase = true)
        }
        score += (matchingTagsCount * 0.10f).coerceAtMost(0.30f)

        // 3. Nghệ sĩ (Artist)
        if (seedArtists.any { it.equals(track.artist, ignoreCase = true) }) {
            score += 0.50f
        }

        // 4. Nghe hết (Complete)
        if (completedTrackIds.contains(track.id)) {
            score += 0.08f
        }

        // 5. Thả tim (Like)
        if (likedTrackIds.contains(track.id) || track.isLiked) {
            score += 0.30f
        }

        // 6. Bỏ qua sớm (Skip)
        if (skippedTrackIds.contains(track.id)) {
            score -= 0.25f
        }

        // 7. Phạt nghe gần đây (Recency penalty < 24h)
        val lastPlayed = playHistoryTimestamps[track.id]
        if (lastPlayed != null && (System.currentTimeMillis() - lastPlayed) < 24 * 60 * 60 * 1000L) {
            score -= 0.30f
        }

        return score
    }
}
