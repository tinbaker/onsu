package com.onsu.ui

/**
 * Trạng thái tải nhạc hiển thị trên UI.
 *
 * `progress` chỉ chứa những bài **đang** tải; giá trị là 0f..1f, hoặc `null` đã được
 * lọc bỏ nên không cần phân biệt "chưa bắt đầu" với "đang tải".
 */
data class DownloadUiState(
    val downloadedIds: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap()
) {
    fun isDownloaded(trackId: String): Boolean = downloadedIds.contains(trackId)

    fun progressOf(trackId: String): Float? = progress[trackId]

    fun isDownloading(trackId: String): Boolean = progress.containsKey(trackId)

    companion object {
        val Empty = DownloadUiState()
    }
}
