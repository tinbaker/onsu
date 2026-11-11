package com.onsu.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.onsu.data.download.DownloadStore
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.i18n.tr
import com.onsu.scoring.QueueManager
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerConnection(
    private val context: Context,
    private val queueManager: QueueManager,
    private val scope: CoroutineScope
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var positionTickerJob: Job? = null
    private var queueObserverJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionTicker()
            } else {
                positionTickerJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = controller ?: return
            _durationMs.value = player.duration.coerceAtLeast(0L)
            _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)

            if (playbackState == Player.STATE_ENDED) {
                // Hết danh sách: ghi nhận đã nghe hết. Không tự nhảy bài ở đây —
                // việc nạp thêm bài do cầu nối queue đảm nhiệm.
                queueManager.currentTrack?.let { queueManager.recordComplete(it.id) }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e(
                "PlayerConnection",
                "Playback error: ${error.errorCodeName} - ${error.message}",
                error
            )
            _isPlaying.value = false
            val track = queueManager.currentTrack
            track?.let { queueManager.recordSkip(it.id) }
            val reason = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                    tr(
                        "No permission to read audio. Grant it in app settings.",
                        "Chưa có quyền đọc âm thanh. Hãy cấp quyền trong Cài đặt ứng dụng."
                    )
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    tr(
                        "This file is no longer on the device. Rescan your library.",
                        "Tệp không còn trên máy. Hãy quét lại thư viện."
                    )
                else -> tr(
                    "Couldn't read the file, or this audio format isn't supported.",
                    "Không đọc được tệp hoặc định dạng âm thanh chưa được hỗ trợ."
                )
            }
            _playbackError.value = "${track?.title ?: tr("Track", "Bài hát")}: $reason"
            // PlaybackService alone owns recovery; two listeners skipping the
            // same error could otherwise jump past the user's selected song.
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = controller ?: return
            _playbackError.value = null
            _currentPositionMs.value = 0L
            _durationMs.value = player.duration.coerceAtLeast(0L)

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Chuyển bài tự động: queue tự tiến và có thể nạp thêm đuôi.
                queueManager.moveToNextTrack()
            } else {
                // Người dùng bấm next/prev hoặc seek sang bài khác: player là nguồn sự thật,
                // kéo index của queue theo để hai bên không lệch.
                queueManager.selectTrackIndex(player.currentMediaItemIndex)
            }
        }
    }

    fun connectAsync(onConnected: (() -> Unit)? = null) {
        if (controller != null) {
            onConnected?.invoke()
            return
        }

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    _isConnected.value = true
                    _isPlaying.value = mediaController.isPlaying
                    _currentPositionMs.value = mediaController.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = mediaController.duration.coerceAtLeast(0L)

                    onConnected?.invoke()
                } catch (_: Exception) {
                    _isConnected.value = false
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Gắn queue vào player: mọi thay đổi của [QueueManager] được phản chiếu sang
     * danh sách media item của ExoPlayer bằng add/remove — không dùng setMediaItems
     * nên không làm gián đoạn bài đang phát.
     *
     * Đây chính là chỗ trước kia bị thiếu: queue có nạp thêm bài nhưng player
     * không bao giờ được báo, nên app dừng ở bài thứ 20.
     */
    fun attachQueue() {
        if (queueObserverJob != null) return

        queueObserverJob = scope.launch(Dispatchers.Main) {
            combine(queueManager.queueState, queueManager.currentIndex) { queue, index ->
                queue to index
            }.collect { (queue, index) ->
                applyReconcile(queue, index)
            }
        }
    }

    private fun applyReconcile(queue: List<Track>, queueIndex: Int) {
        val player = controller ?: return

        val playerIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val ops = QueueReconciler.computeOps(
            playerIds = playerIds,
            playerCurrentIndex = player.currentMediaItemIndex,
            queue = queue,
            queueCurrentIndex = queueIndex
        )
        if (ops.isEmpty()) return

        for (op in ops) {
            when (op) {
                is QueueOp.FullReset -> {
                    player.setMediaItems(
                        queue.map { mediaItemFor(it) },
                        op.startIndex,
                        0L
                    )
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                }

                is QueueOp.TrimHead -> player.removeMediaItems(0, op.count)

                is QueueOp.TrimTail ->
                    player.removeMediaItems(op.fromIndex, player.mediaItemCount)

                is QueueOp.AppendTail -> player.addMediaItems(
                    op.fromIndex,
                    op.tracks.map { mediaItemFor(it) }
                )
            }
        }
    }

    /** Reopening the Activity must not reset a queue still owned by the service. */
    fun adoptExistingQueue(pool: List<Track>): Boolean {
        val player = controller ?: return false
        if (player.mediaItemCount == 0) return false
        val byId = pool.associateBy { it.id }
        val tracks = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            byId[item.mediaId] ?: run {
                val metadata = item.mediaMetadata
                val uri = item.localConfiguration?.uri
                Track(
                    id = item.mediaId,
                    title = metadata.title?.toString() ?: tr("Unknown title", "Không rõ tên bài"),
                    artist = metadata.artist?.toString() ?: tr("Unknown artist", "Không rõ nghệ sĩ"),
                    album = metadata.albumTitle?.toString() ?: "Single",
                    artworkUrl = metadata.artworkUri?.toString(),
                    durationMs = if (index == player.currentMediaItemIndex) player.duration.coerceAtLeast(0L) else 0L,
                    genre = metadata.genre?.toString() ?: "Local Audio",
                    source = if (item.mediaId.startsWith("local_") || uri?.scheme in listOf("content", "file"))
                        TrackSource.LOCAL else TrackSource.AUDIUS,
                    audioUrl = uri?.toString().orEmpty()
                )
            }
        }
        queueManager.replaceQueue(tracks, player.currentMediaItemIndex)
        return true
    }

    fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        initialPositionMs: Long = 0L,
        autoPlay: Boolean = false
    ) {
        val player = controller ?: return
        if (tracks.isEmpty()) return

        _playbackError.value = null
        // Danh sách rỗng là trạng thái có thật khi đang offline (không có gợi ý nào);
        // setMediaItems với chỉ số âm sẽ ném lỗi nên phải chặn trước.
        if (tracks.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            return
        }
        val mediaItems = tracks.map { mediaItemFor(it) }
        player.setMediaItems(mediaItems, startIndex.coerceIn(0, tracks.lastIndex), initialPositionMs)
        player.prepare()
        if (autoPlay) player.play() else player.pause()
    }

    /**
     * Mọi media item đều đi qua đây: nếu bài đã tải về máy thì dùng `content://` của
     * tệp trong thư mục `Music/Onsu` thay cho URL stream, nhờ vậy phát được khi mất
     * mạng mà không phải sửa queue hay session.
     */
    private fun mediaItemFor(track: Track): MediaItem =
        MediaItemBuilder.fromTrack(track, DownloadStore.uriFor(track.id))

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int = 0, initialPositionMs: Long = 0L) {
        setQueue(tracks, startIndex, initialPositionMs, autoPlay = true)
    }

    /**
     * Phát một danh sách bài rời (playlist, kết quả tìm kiếm, nhạc local).
     * Queue phải được thay trước, nếu không cầu nối sẽ kéo player về queue cũ.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        queueManager.replaceQueue(tracks, safeIndex)
        setQueueAndPlay(tracks, safeIndex, 0L)
    }

    fun playTrackAtIndex(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            play()
        }
    }

    fun play() {
        val player = controller ?: return
        _playbackError.value = null
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() {
        controller?.pause()
    }

    /**
     * Dừng phát và bỏ toàn bộ media item — dùng khi đổi/đăng xuất tài khoản để người
     * tiếp theo không nghe tiếp hàng đợi của người trước.
     */
    fun stopAndClear() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    /**
     * Chỉ điều khiển player. Index của queue được kéo theo trong
     * [playerListener.onMediaItemTransition], tránh việc cả hai bên cùng tăng
     * một lúc dẫn tới nhảy 2 bài.
     */
    fun skipNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            play()
        }
    }

    fun skipPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            return
        }
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            play()
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch(Dispatchers.Main) {
            while (isActive && _isPlaying.value) {
                delay(250)
                controller?.let { player ->
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
            }
        }
    }

    fun release() {
        queueObserverJob?.cancel()
        queueObserverJob = null
        positionTickerJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        _isConnected.value = false
    }
}
