package com.onsu.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onsu.data.models.Playlist
import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.i18n.LocalStrings
import com.onsu.i18n.tasteLabel
import com.onsu.ui.DownloadUiState
import com.onsu.ui.theme.MixtapeTokens
import com.onsu.ui.theme.MixtapeTypography

@Composable
fun CharacterBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MixtapeTokens.BgSurface, RoundedCornerShape(16.dp))
            .border(1.dp, MixtapeTokens.BgRaised, RoundedCornerShape(16.dp))
            .padding(MixtapeTokens.Space3)
            .testTag("mixo_character_bubble")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(MixtapeTokens.AccentPrimary, CircleShape)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(5.dp).background(Color.White, CircleShape))
                Box(modifier = Modifier.size(5.dp).background(Color.White, CircleShape))
            }
        }

        Spacer(modifier = Modifier.width(MixtapeTokens.Space3))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mixo",
                style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MixtapeTokens.AccentPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                style = MixtapeTypography.bodyMedium,
                color = MixtapeTokens.TextPrimary
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val alpha = if (track.isPlayable) 1f else 0.45f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(MixtapeTokens.TrackRowHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrentTrack) MixtapeTokens.BgRaised else Color.Transparent)
            .clickable(enabled = track.isPlayable) { onClick() }
            .padding(horizontal = MixtapeTokens.Space4)
            .testTag("track_row_${track.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MixtapeTokens.BgSurface)
        ) {
            if (!track.isPlayable) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = strings.unplayable,
                    tint = MixtapeTokens.StateWarn,
                    modifier = Modifier.size(24.dp)
                )
            } else if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isCurrentTrack && isPlaying) {
                EqualizerIndicator()
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrentTrack) MixtapeTokens.AccentPrimary else MixtapeTokens.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(MixtapeTokens.Space3))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = track.title,
                style = MixtapeTypography.bodyLarge.copy(
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrentTrack) MixtapeTokens.AccentPrimary else MixtapeTokens.TextPrimary.copy(alpha = alpha)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(source = track.source)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (track.isPlayable) {
                        "${track.artist} · ${track.durationFormatted}"
                    } else {
                        track.unplayableReason ?: strings.trackUnplayableFallback
                    },
                    style = MixtapeTypography.labelSmall,
                    color = if (!track.isPlayable) MixtapeTokens.StateWarn else MixtapeTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (track.isPlayable) {
            IconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(40.dp).testTag("like_button_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isLiked) strings.unlike else strings.like,
                    tint = if (track.isLiked) Color(0xFFFF3366) else MixtapeTokens.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(40.dp).testTag("playlist_add_button_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = strings.addToPlaylistAction,
                        tint = MixtapeTokens.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Bài trong máy vốn đã nằm trên thiết bị nên không có gì để tải.
            if (track.source != TrackSource.LOCAL) {
                IconButton(
                    onClick = onToggleDownload,
                    enabled = !isDownloading,
                    modifier = Modifier.size(40.dp).testTag("download_button_${track.id}")
                ) {
                    if (isDownloading) {
                        if (downloadProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                color = MixtapeTokens.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MixtapeTokens.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (track.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = if (track.isDownloaded) {
                                strings.removeDownloadAction
                            } else {
                                strings.downloadAction
                            },
                            tint = if (track.isDownloaded) MixtapeTokens.StateSuccess else MixtapeTokens.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * [TrackRow] đã hợp nhất trạng thái tải nhạc của bài hát.
 *
 * `queue`, `likedTracks`, kết quả tìm kiếm đều là ảnh chụp dữ liệu từ nguồn khác
 * nhau, nên cờ `isDownloaded` được áp tại chỗ hiển thị thay vì phải sửa từng danh sách.
 */
@Composable
fun DownloadAwareTrackRow(
    track: Track,
    downloadState: DownloadUiState,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val downloaded = downloadState.isDownloaded(track.id)
    TrackRow(
        track = if (downloaded) track.copy(isDownloaded = true) else track,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        onClick = onClick,
        onToggleLike = onToggleLike,
        onToggleDownload = onToggleDownload,
        onAddToPlaylist = onAddToPlaylist,
        isDownloading = downloadState.isDownloading(track.id),
        downloadProgress = downloadState.progressOf(track.id) ?: 0f,
        modifier = modifier
    )
}

@Composable
fun SourceBadge(source: TrackSource) {
    val strings = LocalStrings.current

    when (source) {
        TrackSource.LOCAL -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MixtapeTokens.SourceLocal.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Box(
                    modifier = Modifier.size(6.dp).background(MixtapeTokens.SourceLocal, CircleShape)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = strings.onDeviceBadge,
                    style = MixtapeTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MixtapeTokens.SourceLocal
                )
            }
        }
        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(MixtapeTokens.SourceOnline.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Box(
                    modifier = Modifier.size(6.dp).background(MixtapeTokens.SourceOnline, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (source == TrackSource.JAMENDO) "JAM" else "AUD",
                    style = MixtapeTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MixtapeTokens.SourceOnline
                )
            }
        }
    }
}

@Composable
fun EqualizerIndicator() {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.7f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height((16 * bar1).dp).background(MixtapeTokens.AccentPrimary, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).height((16 * bar2).dp).background(MixtapeTokens.AccentPrimary, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).height((16 * bar3).dp).background(MixtapeTokens.AccentPrimary, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun LevelSegmented(
    currentLevel: TasteLevel,
    onSelectLevel: (TasteLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MixtapeTokens.BgSurface, RoundedCornerShape(24.dp))
            .border(1.dp, MixtapeTokens.BgRaised, RoundedCornerShape(24.dp))
            .padding(4.dp)
            .testTag("level_segmented_control")
    ) {
        TasteLevel.values().forEach { level ->
            val isSelected = level == currentLevel
            val bgCol by animateColorAsState(
                targetValue = if (isSelected) MixtapeTokens.AccentPrimary else Color.Transparent,
                label = "seg_bg"
            )
            val textCol by animateColorAsState(
                targetValue = if (isSelected) Color.White else MixtapeTokens.TextSecondary,
                label = "seg_txt"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgCol)
                    .clickable { onSelectLevel(level) }
                    .testTag("level_button_${level.name}")
            ) {
                Text(
                    text = strings.tasteLabel(level),
                    style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textCol
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val progress = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(MixtapeTokens.MiniPlayerHeight)
            .background(MixtapeTokens.BgRaised)
            .clickable { onOpenNowPlaying() }
            .testTag("mini_player")
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(2.dp).background(MixtapeTokens.SliderTrack)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(progress).height(2.dp).background(MixtapeTokens.AccentPrimary)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = MixtapeTokens.Space4)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MixtapeTokens.BgSurface)
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MixtapeTokens.AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(MixtapeTokens.Space3))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MixtapeTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MixtapeTokens.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${formatTime(currentPosMs)} / ${track.durationFormatted}",
                    style = MixtapeTypography.labelSmall,
                    color = MixtapeTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp).testTag("mini_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) strings.pause else strings.play,
                    tint = MixtapeTokens.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp).testTag("mini_skip_next")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = strings.nextTrack,
                    tint = MixtapeTokens.TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    track: Track,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylistAndAdd: ((String) -> Unit)? = null,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MixtapeTokens.BgBase,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MixtapeTokens.BgRaised)
            )
        },
        modifier = Modifier.testTag("now_playing_sheet")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MixtapeTokens.Space6)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.closePlayer,
                        tint = MixtapeTokens.TextSecondary
                    )
                }

                Text(
                    text = strings.nowPlaying,
                    style = MixtapeTypography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                    color = MixtapeTokens.TextSecondary
                )

                SourceBadge(source = track.source)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MixtapeTokens.BgSurface)
                    .border(1.dp, MixtapeTokens.BgRaised, RoundedCornerShape(24.dp))
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .background(MixtapeTokens.AccentPrimary.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MixtapeTokens.AccentPrimary,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = track.genre,
                            style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MixtapeTokens.AccentPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = track.title,
                style = MixtapeTypography.displayLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${track.artist} · ${track.album}",
                style = MixtapeTypography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MixtapeTokens.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            DurationSeekBar(
                currentPosMs = currentPosMs,
                durationMs = durationMs,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier.size(48.dp).testTag("seek_prev_button")
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = strings.previousTrack,
                        tint = MixtapeTokens.TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MixtapeTokens.AccentPrimary)
                        .clickable { onPlayPause() }
                        .testTag("seek_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) strings.pause else strings.play,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp).testTag("seek_next_button")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = strings.nextTrack,
                        tint = MixtapeTokens.TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MixtapeTokens.BgSurface, RoundedCornerShape(16.dp))
                    .padding(vertical = 8.dp)
            ) {
                IconButton(onClick = onToggleLike, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (track.isLiked) strings.unlike else strings.like,
                        tint = if (track.isLiked) Color(0xFFFF3366) else MixtapeTokens.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onToggleDownload, enabled = !isDownloading, modifier = Modifier.size(48.dp)) {
                    if (isDownloading) {
                        if (downloadProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                color = MixtapeTokens.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MixtapeTokens.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (track.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = if (track.isDownloaded) {
                                strings.removeDownloadAction
                            } else {
                                strings.downloadAction
                            },
                            tint = if (track.isDownloaded) MixtapeTokens.StateSuccess else MixtapeTokens.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(onClick = { showAddToPlaylistDialog = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = strings.addToPlaylistAction,
                        tint = MixtapeTokens.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onSelect = {
                onAddToPlaylist(it)
                showAddToPlaylistDialog = false
            },
            onCreateAndSelect = { newName ->
                onCreatePlaylistAndAdd?.invoke(newName)
                showAddToPlaylistDialog = false
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }
}

@Composable
fun DurationSeekBar(
    currentPosMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1000L).toFloat()
    val sliderValue = if (isDragging) dragPosition else (currentPosMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().testTag("duration_seek_bar")) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * safeDuration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MixtapeTokens.SliderThumb,
                activeTrackColor = MixtapeTokens.AccentPrimary,
                inactiveTrackColor = MixtapeTokens.SliderTrack
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            val displayedCurrentMs = if (isDragging) (dragPosition * safeDuration).toLong() else currentPosMs
            Text(
                text = formatTime(displayedCurrentMs),
                style = MixtapeTypography.labelSmall,
                color = MixtapeTokens.TextSecondary
            )
            Text(
                text = formatTime(durationMs),
                style = MixtapeTypography.labelSmall,
                color = MixtapeTokens.TextSecondary
            )
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onSelect: (String) -> Unit,
    onCreateAndSelect: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var isCreating by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("dialog_add_to_playlist")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.addToPlaylistTitle, style = MixtapeTypography.titleLarge)
                    if (!isCreating && onCreateAndSelect != null) {
                        TextButton(onClick = { isCreating = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.newPlaylist, color = MixtapeTokens.AccentPrimary, style = MixtapeTypography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCreating) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MixtapeTokens.BgRaised, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(strings.newPlaylistName, style = MixtapeTypography.labelMedium, color = MixtapeTokens.TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text(strings.playlistNameExample) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { isCreating = false; newPlaylistName = "" }) {
                                Text(strings.cancel, color = MixtapeTokens.TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreateAndSelect?.invoke(newPlaylistName.trim())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
                                enabled = newPlaylistName.isNotBlank()
                            ) {
                                Text(strings.createAndAdd, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (playlists.isEmpty() && !isCreating) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strings.noPlaylists, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (onCreateAndSelect != null) {
                                Button(
                                    onClick = { isCreating = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary)
                                ) {
                                    Text(strings.createFirstPlaylistShort, color = Color.White)
                                }
                            }
                        }
                    }
                } else if (!isCreating) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(pl.id) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(MixtapeTokens.BgRaised)
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(pl.title, style = MixtapeTypography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(strings.close, color = MixtapeTokens.TextSecondary)
                }
            }
        }
    }
}

@Composable
fun DeleteBatchConfirmDialog(
    tracksToDelete: List<Track>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val totalSizeMb = tracksToDelete.sumOf { it.fileSizeBytes }.toDouble() / (1024 * 1024)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MixtapeTokens.StateDanger)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.deleteBrokenTitle, style = MixtapeTypography.titleLarge, color = MixtapeTokens.TextPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.deleteBrokenBody.format(tracksToDelete.size, "%.1f".format(totalSizeMb)),
                    style = MixtapeTypography.bodyMedium,
                    color = MixtapeTokens.TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MixtapeTokens.BgRaised, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    tracksToDelete.forEach { track ->
                        Text(
                            text = "• ${track.title} (${track.fileSizeMbFormatted})",
                            style = MixtapeTypography.labelSmall,
                            color = MixtapeTokens.TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.StateDanger)
                    ) {
                        Text(strings.deleteForReal, style = MixtapeTypography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
