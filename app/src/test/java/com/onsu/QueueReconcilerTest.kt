package com.onsu

import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.player.QueueOp
import com.onsu.player.QueueReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra phần so khớp queue ↔ player.
 *
 * Đây là logic thuần nên test được trên JVM, không cần emulator. Nhờ nó mà bug
 * "hết 20 bài là dừng phát" có thể tái hiện bằng một assertion thay vì ngồi nghe
 * cho tới bài thứ 20.
 */
class QueueReconcilerTest {

    private fun track(id: String): Track = Track(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        durationMs = 200_000L,
        genre = "Pop",
        source = TrackSource.AUDIUS,
        audioUrl = "https://example.com/$id.mp3"
    )

    private fun tracks(range: IntRange): List<Track> = range.map { track("t$it") }

    @Test
    fun `khi player va queue khop hoan toan thi khong lam gi`() {
        val queue = tracks(0..19)
        val ops = QueueReconciler.computeOps(
            playerIds = queue.map { it.id },
            playerCurrentIndex = 4,
            queue = queue,
            queueCurrentIndex = 4
        )

        assertTrue("Không được đụng vào player khi hai bên đã khớp", ops.isEmpty())
    }

    @Test
    fun `queue nap them bai thi chi append phan duoi con thieu`() {
        val queue = tracks(0..29)
        val playerIds = tracks(0..19).map { it.id }

        val ops = QueueReconciler.computeOps(
            playerIds = playerIds,
            playerCurrentIndex = 12,
            queue = queue,
            queueCurrentIndex = 12
        )

        assertEquals(1, ops.size)
        val append = ops.single() as QueueOp.AppendTail
        assertEquals(20, append.fromIndex)
        assertEquals(10, append.tracks.size)
        assertEquals("t20", append.tracks.first().id)
    }

    @Test
    fun `doi gu nhac thay duoi thi cat duoi roi noi lai`() {
        val oldQueue = tracks(0..19)
        val newQueue = oldQueue.take(8) + tracks(100..111)

        val ops = QueueReconciler.computeOps(
            playerIds = oldQueue.map { it.id },
            playerCurrentIndex = 7,
            queue = newQueue,
            queueCurrentIndex = 7
        )

        assertEquals(2, ops.size)
        assertEquals(QueueOp.TrimTail(8), ops[0])
        val append = ops[1] as QueueOp.AppendTail
        assertEquals(8, append.fromIndex)
        assertEquals(12, append.tracks.size)
    }

    @Test
    fun `head trim thi bo dung so bai da phat o dau danh sach`() {
        val fullQueue = tracks(0..19)
        // Sliding window đã cắt 5 bài đầu, queue chỉ còn từ t5
        val trimmedQueue = fullQueue.drop(5)

        val ops = QueueReconciler.computeOps(
            playerIds = fullQueue.map { it.id },
            playerCurrentIndex = 9,          // đang phát t9 trong player
            queue = trimmedQueue,
            queueCurrentIndex = 4            // trong queue mới, t9 nằm ở index 4
        )

        assertEquals(1, ops.size)
        assertEquals(QueueOp.TrimHead(5), ops.single())
    }

    @Test
    fun `bai dang phat khong con trong queue thi phai dung lai toan bo`() {
        val queue = tracks(50..69)

        val ops = QueueReconciler.computeOps(
            playerIds = tracks(0..19).map { it.id },
            playerCurrentIndex = 3,
            queue = queue,
            queueCurrentIndex = 3
        )

        assertEquals(1, ops.size)
        assertEquals(QueueOp.FullReset(3), ops.single())
    }

    @Test
    fun `player rong thi dung lai tu dau`() {
        val queue = tracks(0..19)

        val ops = QueueReconciler.computeOps(
            playerIds = emptyList(),
            playerCurrentIndex = 0,
            queue = queue,
            queueCurrentIndex = 0
        )

        assertEquals(listOf<QueueOp>(QueueOp.FullReset(0)), ops)
    }

    @Test
    fun `queue rong thi khong dung vao player`() {
        val ops = QueueReconciler.computeOps(
            playerIds = tracks(0..3).map { it.id },
            playerCurrentIndex = 1,
            queue = emptyList(),
            queueCurrentIndex = 0
        )

        assertTrue(ops.isEmpty())
    }

    @Test
    fun `head trim va nap them duoi xay ra cung luc`() {
        val playerIds = tracks(0..24).map { it.id }
        // queue đã cắt 6 bài đầu và nạp thêm 5 bài mới ở cuối
        val queue = tracks(6..29)

        val ops = QueueReconciler.computeOps(
            playerIds = playerIds,
            playerCurrentIndex = 12,   // t12
            queue = queue,
            queueCurrentIndex = 6      // t12 ở index 6
        )

        assertEquals(2, ops.size)
        assertEquals(QueueOp.TrimHead(6), ops[0])
        val append = ops[1] as QueueOp.AppendTail
        // Sau TrimHead(6) player còn 19 item (t6..t24) ⇒ nối tiếp vào index 19
        assertEquals(19, append.fromIndex)
        assertEquals(5, append.tracks.size)
        assertEquals("t25", append.tracks.first().id)
    }
}
