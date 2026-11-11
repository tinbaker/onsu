package com.onsu.player

import com.onsu.data.models.Track

/**
 * Một thay đổi cần áp lên ExoPlayer để player khớp với queue.
 *
 * [QueueReconciler.computeOps] trả về danh sách rỗng khi hai bên đã khớp nhau —
 * nghĩa là không đụng gì vào player, tránh giật âm thanh mỗi khi queue phát lại
 * một StateFlow không thay đổi.
 */
sealed interface QueueOp {
    /** Dựng lại toàn bộ danh sách (chỉ dùng khi player và queue lệch hẳn nhau). */
    data class FullReset(val startIndex: Int) : QueueOp

    /** Bỏ [count] bài đã phát ở đầu danh sách (head-trim của sliding window). */
    data class TrimHead(val count: Int) : QueueOp

    /** Cắt đuôi từ [fromIndex] tới hết. */
    data class TrimTail(val fromIndex: Int) : QueueOp

    /** Nối thêm bài vào vị trí [fromIndex]. */
    data class AppendTail(val fromIndex: Int, val tracks: List<Track>) : QueueOp
}

/**
 * So khớp danh sách media item của player với queue của [com.onsu.scoring.QueueManager].
 *
 * Hàm thuần, không phụ thuộc Android ⇒ test được trên JVM. Nhờ vậy toàn bộ logic
 * "khi nào thêm bài, khi nào cắt bài, khi nào phải dựng lại" nằm ở một chỗ duy nhất
 * thay vì rải rác trong listener của Media3.
 *
 * Các op trả về được áp theo đúng thứ tự: head trước, đuôi sau. Sau khi TrimHead(k),
 * chỉ số hiện tại của player trở thành [queueCurrentIndex], nên các chỉ số trong op
 * đuôi đều tính theo hệ toạ độ đó.
 */
object QueueReconciler {

    fun computeOps(
        playerIds: List<String>,
        playerCurrentIndex: Int,
        queue: List<Track>,
        queueCurrentIndex: Int
    ): List<QueueOp> {
        if (queue.isEmpty()) return emptyList()

        val target = queueCurrentIndex.coerceIn(0, queue.lastIndex)
        val currentId = queue[target].id
        val playerCurrentId = playerIds.getOrNull(playerCurrentIndex)

        // Player rỗng, hoặc đang phát một bài không còn trong queue ⇒ không thể vá, phải dựng lại.
        if (playerIds.isEmpty() || playerCurrentId != currentId) {
            return listOf(QueueOp.FullReset(target))
        }

        val ops = mutableListOf<QueueOp>()

        // --- Đầu danh sách: queue chỉ có thể ngắn hơn player (head-trim) ---
        val playerHead = playerIds.take(playerCurrentIndex)
        val queueHeadIds = queue.take(target).map { it.id }
        if (queueHeadIds.size > playerHead.size ||
            queueHeadIds != playerHead.takeLast(queueHeadIds.size)
        ) {
            return listOf(QueueOp.FullReset(target))
        }
        val trimCount = playerHead.size - queueHeadIds.size
        if (trimCount > 0) ops += QueueOp.TrimHead(trimCount)

        // --- Đuôi danh sách: nạp thêm (append) hoặc đổi gu (replace tail) ---
        val playerTail = playerIds.drop(playerCurrentIndex + 1)
        val queueTail = queue.drop(target + 1)
        val queueTailIds = queueTail.map { it.id }

        // Phần đuôi khớp nhau khi đoạn chung (tính theo độ dài ngắn hơn) trùng khít.
        // So sánh đúng theo cả hai chiều: queue có thể dài hơn (nạp thêm) hoặc ngắn hơn (cắt bớt).
        val commonTail = minOf(playerTail.size, queueTailIds.size)
        val tailMatches = playerTail.take(commonTail) == queueTailIds.take(commonTail)

        if (!tailMatches) {
            ops += QueueOp.TrimTail(target + 1)
            if (queueTail.isNotEmpty()) ops += QueueOp.AppendTail(target + 1, queueTail)
        } else {
            when {
                queueTail.size > playerTail.size -> ops += QueueOp.AppendTail(
                    fromIndex = target + 1 + playerTail.size,
                    tracks = queueTail.drop(playerTail.size)
                )
                queueTail.size < playerTail.size -> ops += QueueOp.TrimTail(
                    fromIndex = target + 1 + queueTail.size
                )
            }
        }

        return ops
    }
}
