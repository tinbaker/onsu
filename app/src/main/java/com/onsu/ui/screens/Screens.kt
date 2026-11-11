package com.onsu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.onsu.data.auth.GoogleAccount
import com.onsu.data.auth.GoogleSignInResult
import com.onsu.data.auth.OtpResult
import com.onsu.data.auth.OtpService
import com.onsu.data.auth.toUserProfile
import com.onsu.data.models.Playlist
import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.UserProfile
import com.onsu.i18n.AppLanguage
import com.onsu.i18n.AppStrings
import com.onsu.i18n.LocalStrings
import com.onsu.i18n.tasteLabel
import com.onsu.ui.DownloadUiState
import com.onsu.ui.components.CharacterBubble
import com.onsu.ui.components.DownloadAwareTrackRow
import com.onsu.ui.components.LevelSegmented
import com.onsu.ui.components.TrackRow
import com.onsu.ui.theme.AppThemeMode
import com.onsu.ui.theme.MixtapeTokens
import com.onsu.ui.theme.MixtapeTypography
import kotlinx.coroutines.launch

val AvailableSeedGenres = listOf(
    "Pop", "Indie", "Ballad", "Hip-hop", "R&B",
    "Lo-Fi", "Rock", "Acoustic", "EDM", "Electronic"
)

@Composable
fun OnboardingSeedsScreen(
    selectedGenres: Set<String>,
    canProceed: Boolean,
    onToggleGenre: (String) -> Unit,
    onProceed: () -> Unit
) {
    val strings = LocalStrings.current
    val totalSelected = selectedGenres.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MixtapeTokens.Space4)
            .testTag("screen_onboarding_seeds")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                text = strings.stepLabel.format("1"),
                style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MixtapeTokens.AccentPrimary
            )
            Text(strings.stepCounter, style = MixtapeTypography.labelSmall, color = MixtapeTokens.TextSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        CharacterBubble(message = strings.seedsBubble)

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = strings.favoriteGenres,
                style = MixtapeTypography.titleLarge,
                color = MixtapeTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.genresHint,
                style = MixtapeTypography.labelSmall,
                color = MixtapeTokens.TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChipsGrid(
                items = AvailableSeedGenres,
                selected = selectedGenres,
                onToggle = onToggleGenre
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.selectedCount.format(totalSelected),
            style = MixtapeTypography.bodyMedium,
            color = if (canProceed) MixtapeTokens.StateSuccess else MixtapeTokens.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onProceed,
            enabled = canProceed,
            colors = ButtonDefaults.buttonColors(
                containerColor = MixtapeTokens.AccentPrimary,
                disabledContainerColor = MixtapeTokens.BgRaised
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_continue_button")
        ) {
            Text(
                text = if (canProceed) strings.continueLabel else strings.selectAtLeastThree,
                style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (canProceed) Color.White else MixtapeTokens.TextDisabled
            )
        }
    }
}

@Composable
fun ChipsGrid(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    val isChecked = selected.contains(item)
                    FilterChip(
                        selected = isChecked,
                        onClick = { onToggle(item) },
                        label = { Text(item, style = MixtapeTypography.bodyMedium) },
                        leadingIcon = if (isChecked) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MixtapeTokens.AccentPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = MixtapeTokens.BgSurface,
                            labelColor = MixtapeTokens.TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("chip_${item.replace(" ", "_")}")
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun OnboardingFolderGuideScreen(
    onOpenFileManager: () -> Unit,
    onSkip: () -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxSize().padding(MixtapeTokens.Space4).testTag("screen_onboarding_folder")
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = strings.stepLabel.format("2"),
                style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MixtapeTokens.AccentPrimary
            )
            Text("2/2", style = MixtapeTypography.labelSmall, color = MixtapeTokens.TextSecondary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        CharacterBubble(message = strings.folderBubble)
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.folderStandardLocation,
                    style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MixtapeTokens.TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MixtapeTokens.StateSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Music/", style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(strings.folderPutHere, style = MixtapeTypography.labelSmall, color = MixtapeTokens.StateSuccess)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MixtapeTokens.BgRaised)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MixtapeTokens.TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download/", style = MixtapeTypography.bodyMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MixtapeTokens.BgRaised)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MixtapeTokens.TextDisabled)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App_Cache/", style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextDisabled)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(strings.folderNotVisible, style = MixtapeTypography.labelSmall, color = MixtapeTokens.TextDisabled)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onOpenFileManager,
            colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("folder_open_manager_button")
        ) {
            Text(strings.startListening, style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSkip,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("folder_skip_button")
        ) {
            Text(strings.later, style = MixtapeTypography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAutoPlaylistScreen(
    queue: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    activeStyle: TasteLevel,
    onSelectStyle: (TasteLevel) -> Unit,
    onPlayTrackIndex: (Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit,
    downloadState: DownloadUiState = DownloadUiState.Empty,
    onAddToPlaylist: (Track) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    isRefreshing: Boolean = false,
    canLoadMore: Boolean = true,
    isOffline: Boolean = false,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()

    // `queue` is a new list on every emission, so the size has to be tracked as state
    // for the derived value below to stay fresh inside a long-lived LazyListState.
    val queueSize by rememberUpdatedState(queue.size)

    val reachedEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            queueSize > 0 && lastVisible >= queueSize - 2
        }
    }

    // Only one page per distinct tail: after appending, the last id changes, so the
    // next page can only be requested by scrolling again. Without this guard a short
    // screen would request pages in a loop.
    var loadedAnchor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reachedEnd, canLoadMore, queue.size) {
        if (!canLoadMore || !reachedEnd) return@LaunchedEffect
        val anchor = queue.lastOrNull()?.id ?: return@LaunchedEffect
        if (anchor == loadedAnchor) return@LaunchedEffect
        loadedAnchor = anchor
        onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize().testTag("screen_main_auto_playlist")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MixtapeTokens.Space4, end = MixtapeTokens.Space2, top = 10.dp, bottom = 6.dp)
        ) {
            Text(
                text = strings.appName,
                style = MixtapeTypography.displayLarge.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp
                ),
                color = MixtapeTokens.TextPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Search, contentDescription = strings.searchAction, tint = MixtapeTokens.TextPrimary)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = strings.settingsAction, tint = MixtapeTokens.TextPrimary)
            }
        }

        LevelSegmented(
            currentLevel = activeStyle,
            onSelectLevel = onSelectStyle,
            modifier = Modifier.padding(horizontal = MixtapeTokens.Space4, vertical = 6.dp)
        )

        if (isOffline) {
            // Mất mạng thì không có gợi ý nào để hiện: để trống và nói rõ lý do, thay
            // vì lấp bằng kho nhạc dự phòng. Vẫn kéo xuống được để thử lại.
            OfflineNotice(
                isRetrying = isRefreshing,
                onRetry = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else if (queue.isEmpty()) {
            // Trước đây hàng đợi rỗng dẫn tới một khoảng đen không có chữ nào, không
            // phân biệt được "đang tải" với "lỗi". Giờ nói rõ trạng thái và cho lối thoát.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("main_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = MixtapeTokens.Space6)
                ) {
                    Text(
                        text = strings.emptyQueueTitle,
                        style = MixtapeTypography.bodyLarge,
                        color = MixtapeTokens.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = strings.emptyQueueBody,
                        style = MixtapeTypography.labelSmall,
                        color = MixtapeTokens.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("main_empty_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MixtapeTokens.AccentPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isRefreshing) strings.reloading else strings.reloadSuggestions, style = MixtapeTypography.bodyMedium)
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("main_pull_to_refresh")
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(queue.size, key = { index -> queue[index].id }) { index ->
                        val track = queue[index]
                        DownloadAwareTrackRow(
                            track = track,
                            downloadState = downloadState,
                            isCurrentTrack = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            onClick = { onPlayTrackIndex(index) },
                            onToggleLike = { onToggleLike(track) },
                            onToggleDownload = { onToggleDownload(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) }
                        )
                    }

                    item(key = "main_paging_footer") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (canLoadMore) strings.pagingMore else strings.pagingEnd,
                                style = MixtapeTypography.labelSmall,
                                color = MixtapeTokens.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineNotice(
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Nằm trong PullToRefreshBox để thao tác "kéo xuống từ đầu danh sách" vẫn dùng
    // được khi đang offline — người dùng không phải thoát ra vào lại app.
    PullToRefreshBox(
        isRefreshing = isRetrying,
        onRefresh = onRetry,
        modifier = modifier.testTag("main_offline_state")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MixtapeTokens.Space6),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MixtapeTokens.TextSecondary,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = strings.offlineTitle,
                    style = MixtapeTypography.bodyLarge,
                    color = MixtapeTokens.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.offlineBody,
                    style = MixtapeTypography.labelSmall,
                    color = MixtapeTokens.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isRetrying,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("main_offline_retry_button")
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MixtapeTokens.AccentPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isRetrying) strings.retrying else strings.retry, style = MixtapeTypography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    likedTracks: List<Track>,
    playlists: List<Playlist>,
    downloadedTracks: List<Track> = emptyList(),
    downloadState: DownloadUiState = DownloadUiState.Empty,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleDownload: (Track) -> Unit = {},
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null
) {
    val strings = LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().testTag("screen_library")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MixtapeTokens.Space4, vertical = 12.dp)
        ) {
            Text(
                text = strings.libraryTitle,
                style = MixtapeTypography.titleLarge.copy(fontSize = 24.sp),
                color = MixtapeTokens.TextPrimary
            )
            if (selectedTab == 1) {
                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = strings.createPlaylistTitle, tint = MixtapeTokens.AccentPrimary)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MixtapeTokens.Space4, vertical = 4.dp)
        ) {
            val tabs = listOf(
                strings.tabLiked.format(likedTracks.size),
                strings.tabPlaylists.format(playlists.size),
                strings.tabDownloads.format(downloadedTracks.size)
            )
            tabs.forEachIndexed { index, title ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(title, style = MixtapeTypography.bodyMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MixtapeTokens.AccentPrimary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0) {
            if (likedTracks.isEmpty()) {
                EmptyPlaceholder(strings.emptyLikedTitle, strings.emptyLikedBody)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(likedTracks, key = { it.id }) { track ->
                        DownloadAwareTrackRow(
                            track = track,
                            downloadState = downloadState,
                            isCurrentTrack = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            onClick = { onPlayTrack(track) },
                            onToggleLike = { onToggleLike(track) },
                            onToggleDownload = { onToggleDownload(track) },
                            onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                        )
                    }
                }
            }
        } else if (selectedTab == 1) {
            if (playlists.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                ) {
                    EmptyPlaceholder(
                        title = strings.emptyPlaylistsTitle,
                        subtitle = strings.emptyPlaylistsBody
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.createFirstPlaylist, color = Color.White)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = MixtapeTokens.Space4, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 80.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MixtapeTokens.BgRaised)
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.title, style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MixtapeTokens.TextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.privatePlaylist, style = MixtapeTypography.labelSmall, color = MixtapeTokens.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (downloadedTracks.isEmpty()) {
                EmptyPlaceholder(strings.emptyDownloadsTitle, strings.emptyDownloadsBody)
            } else {
                val totalMb = "%.1f".format(downloadedTracks.sumOf { it.fileSizeBytes }.toDouble() / (1024 * 1024))
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    item(key = "downloads_summary") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MixtapeTokens.Space4, vertical = 8.dp)) {
                            Text(
                                text = strings.downloadsSummary.format(downloadedTracks.size, totalMb),
                                style = MixtapeTypography.labelSmall,
                                color = MixtapeTokens.TextSecondary,
                                modifier = Modifier.testTag("downloads_summary")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Nói rõ tệp nằm ở đâu để người dùng mở được bằng ứng dụng
                            // quản lý tệp hoặc app nhạc khác, không chỉ trong app này.
                            Text(
                                text = strings.downloadsLocation,
                                style = MixtapeTypography.labelSmall,
                                color = MixtapeTokens.TextSecondary,
                                modifier = Modifier.testTag("downloads_location_hint")
                            )
                        }
                    }
                    items(downloadedTracks, key = { it.id }) { track ->
                        DownloadAwareTrackRow(
                            track = track,
                            downloadState = downloadState,
                            isCurrentTrack = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            onClick = { onPlayTrack(track) },
                            onToggleLike = { onToggleLike(track) },
                            onToggleDownload = { onToggleDownload(track) },
                            onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(onCreate = { onCreatePlaylist(it); showCreateDialog = false }, onDismiss = { showCreateDialog = false })
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onToggleLike: (Track) -> Unit = {},
    downloadState: DownloadUiState = DownloadUiState.Empty,
    onToggleDownload: (Track) -> Unit = {}
) {
    val strings = LocalStrings.current
    if (playlist == null) return

    Column(modifier = Modifier.fillMaxSize().testTag("screen_playlist_detail")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = MixtapeTokens.TextPrimary)
            }
            Text(strings.playlistDetailTitle, style = MixtapeTypography.titleLarge, color = MixtapeTokens.TextPrimary)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MixtapeTokens.Space4, vertical = 8.dp)
                .background(MixtapeTokens.BgSurface, RoundedCornerShape(16.dp)).padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MixtapeTokens.AccentPrimary.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(playlist.title, style = MixtapeTypography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.playlistMeta.format(tracks.size),
                    style = MixtapeTypography.labelSmall,
                    color = MixtapeTokens.TextSecondary
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.weight(1f)) {
            items(tracks, key = { it.id }) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadAwareTrackRow(
                        track = track,
                        downloadState = downloadState,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onToggleLike = { onToggleLike(track) },
                        onToggleDownload = { onToggleDownload(track) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemoveTrack(track.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = strings.removeTrack, tint = MixtapeTokens.TextDisabled)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    candidatePool: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    downloadState: DownloadUiState = DownloadUiState.Empty,
    onToggleDownload: (Track) -> Unit = {}
) {
    val strings = LocalStrings.current
    val filtered = remember(query, candidatePool) {
        if (query.isBlank()) emptyList()
        else candidatePool.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().testTag("screen_search")) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(strings.searchPlaceholder, color = MixtapeTokens.TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MixtapeTokens.TextSecondary) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = strings.close, tint = MixtapeTokens.TextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MixtapeTokens.BgSurface,
                unfocusedContainerColor = MixtapeTokens.BgSurface,
                focusedBorderColor = MixtapeTokens.AccentPrimary,
                unfocusedBorderColor = MixtapeTokens.BgRaised
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MixtapeTokens.Space4, vertical = 8.dp).testTag("search_text_input")
        )

        if (query.isBlank()) {
            EmptyPlaceholder(strings.searchEmptyTitle, strings.searchEmptyBody)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { track ->
                    DownloadAwareTrackRow(
                        track = track,
                        downloadState = downloadState,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onToggleLike = { onToggleLike(track) },
                        onToggleDownload = { onToggleDownload(track) },
                        onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                    )
                }
            }
        }
    }
}

@Composable
fun LocalMusicScreen(
    localTracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    onToggleLike: (Track) -> Unit = {},
    onAddToPlaylist: ((Track) -> Unit)? = null,
    onScanMusic: () -> Unit,
    onBack: () -> Unit,
    hasAudioPermission: Boolean = true,
    isScanning: Boolean = false,
    errorMessage: String? = null,
    onOpenPermissionSettings: () -> Unit = {}
) {
    val strings = LocalStrings.current

    Column(modifier = Modifier.fillMaxSize().testTag("screen_local_music")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = MixtapeTokens.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.localTitle, style = MixtapeTypography.titleLarge)
                Text(
                    if (!hasAudioPermission) strings.localNoPermission
                    else if (isScanning) strings.localScanning
                    else strings.localFound.format(localTracks.size),
                    style = MixtapeTypography.labelSmall,
                    color = MixtapeTokens.TextSecondary
                )
            }
            IconButton(
                onClick = onScanMusic,
                enabled = !isScanning || !hasAudioPermission,
                modifier = Modifier.size(48.dp).testTag("local_scan_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = strings.scanAgain, tint = MixtapeTokens.AccentPrimary)
            }
        }

        errorMessage?.let {
            Text(
                it,
                style = MixtapeTypography.bodyMedium,
                color = MixtapeTokens.TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("local_audio_error")
            )
        }

        if (!hasAudioPermission) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("local_permission_required")) {
                Text(strings.localPermissionBody)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScanMusic) { Text(strings.grantAudioPermission) }
                TextButton(onClick = onOpenPermissionSettings) { Text(strings.openAppSettings) }
            }
        } else if (isScanning && localTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MixtapeTokens.AccentPrimary)
            }
        } else if (localTracks.isEmpty()) {
            EmptyPlaceholder(strings.localEmptyTitle, strings.localEmptyBody)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.weight(1f)) {
                items(localTracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onToggleLike = { onToggleLike(track) },
                        onToggleDownload = {},
                        onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    user: UserProfile?,
    themeMode: AppThemeMode,
    language: AppLanguage,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocalMusic: () -> Unit,
    onResetOnboarding: () -> Unit,
    onSignOut: (() -> Unit)? = null
) {
    val strings = LocalStrings.current

    LazyColumn(
        contentPadding = PaddingValues(horizontal = MixtapeTokens.Space4, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("screen_settings")
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MixtapeTypography.titleLarge.copy(fontSize = 24.sp),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            ProfileSummaryCard(
                user = user,
                onClick = onOpenProfile
            )
        }

        item {
            SectionLabel(strings.preferencesLabel)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.languageLabel,
                        style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MixtapeTokens.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SettingChoice(
                            label = strings.languageEnglish,
                            selected = language == AppLanguage.ENGLISH,
                            onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                            modifier = Modifier.weight(1f).testTag("language_english")
                        )
                        SettingChoice(
                            label = strings.languageVietnamese,
                            selected = language == AppLanguage.VIETNAMESE,
                            onClick = { onSelectLanguage(AppLanguage.VIETNAMESE) },
                            modifier = Modifier.weight(1f).testTag("language_vietnamese")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MixtapeTokens.BgRaised
                    )

                    Text(
                        text = strings.themeLabel,
                        style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MixtapeTokens.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SettingChoice(
                            label = strings.themeDark,
                            selected = themeMode == AppThemeMode.DARK,
                            onClick = { onSelectTheme(AppThemeMode.DARK) },
                            modifier = Modifier.weight(1f).testTag("theme_dark")
                        )
                        SettingChoice(
                            label = strings.themeLight,
                            selected = themeMode == AppThemeMode.LIGHT,
                            onClick = { onSelectTheme(AppThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f).testTag("theme_light")
                        )
                        SettingChoice(
                            label = strings.themeSystem,
                            selected = themeMode == AppThemeMode.SYSTEM,
                            onClick = { onSelectTheme(AppThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f).testTag("theme_system")
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenLocalMusic() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MixtapeTokens.AccentPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.manageLocalMusic,
                        style = MixtapeTypography.bodyMedium,
                        color = MixtapeTokens.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MixtapeTokens.TextSecondary)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.tasteSectionTitle,
                        style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MixtapeTokens.AccentPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.tasteSectionBody,
                        style = MixtapeTypography.bodyMedium,
                        color = MixtapeTokens.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onResetOnboarding,
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.BgRaised),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_onboarding_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MixtapeTokens.AccentPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.redoOnboarding, color = MixtapeTokens.TextPrimary)
                    }
                }
            }
        }

        if (user?.isLoggedIn == true && onSignOut != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSignOut() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = MixtapeTokens.StateWarn)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = strings.signOutGoogle,
                            color = MixtapeTokens.StateWarn,
                            style = MixtapeTypography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MixtapeTokens.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MixtapeTokens.AccentPrimary else MixtapeTokens.BgRaised)
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else MixtapeTokens.TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileSummaryCard(
    user: UserProfile?,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val loggedIn = user?.isLoggedIn == true

    Card(
        colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            AccountAvatar(user = user, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (loggedIn) user?.displayName.orEmpty() else strings.guestName,
                    style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MixtapeTokens.TextPrimary
                )
                Text(
                    text = if (loggedIn) user?.email.orEmpty() else strings.tapToSignIn,
                    style = MixtapeTypography.labelSmall,
                    color = MixtapeTokens.TextSecondary
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MixtapeTokens.TextSecondary)
        }
    }
}

@Composable
private fun AccountAvatar(user: UserProfile?, size: Dp) {
    val photo = user?.photoUrl
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MixtapeTokens.BgRaised)
            .testTag("settings_avatar")
    ) {
        when {
            user?.isLoggedIn == true && !photo.isNullOrBlank() -> AsyncImage(
                model = photo,
                contentDescription = user.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            user?.isLoggedIn == true -> Text(
                text = user.displayName.trim().take(1).uppercase().ifEmpty { "?" },
                style = MixtapeTypography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6).sp),
                color = MixtapeTokens.AccentPrimary
            )
            else -> Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MixtapeTokens.AccentPrimary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
fun ProfileScreen(
    user: UserProfile?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val loggedIn = user?.isLoggedIn == true

    Column(modifier = Modifier.fillMaxSize().testTag("screen_profile")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = MixtapeTokens.TextPrimary)
            }
            Text(strings.profileTitle, style = MixtapeTypography.titleLarge, color = MixtapeTokens.TextPrimary)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            AccountAvatar(user = user, size = 96.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (loggedIn) user?.displayName.orEmpty() else strings.guestName,
                style = MixtapeTypography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = MixtapeTokens.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (loggedIn) user?.email.orEmpty() else strings.notSignedIn,
                style = MixtapeTypography.bodyMedium,
                color = MixtapeTokens.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MixtapeTokens.AccentPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (loggedIn) strings.accountGoogle else strings.accountGuest,
                    style = MixtapeTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MixtapeTokens.AccentPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MixtapeTokens.Space4)
        ) {
            if (loggedIn) {
                OutlinedButton(
                    onClick = onSignIn,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MixtapeTokens.BgRaised),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_switch_account")
                ) {
                    Text(strings.switchAccount, color = MixtapeTokens.TextPrimary)
                }
                if (onSignOut != null) {
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.BgSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_sign_out")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = MixtapeTokens.StateWarn)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.signOutAction, color = MixtapeTokens.StateWarn)
                    }
                }
            } else {
                Button(
                    onClick = onSignIn,
                    colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_sign_in")
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.continueWithGoogle, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

enum class GoogleAuthStep {
    NONE,
    VERIFY_OTP
}

/**
 * Đăng nhập bằng tài khoản Google thật.
 *
 * Luồng: Credential Manager mở bảng chọn tài khoản của hệ thống → Google trả về ID
 * token → lấy email thật trong token → (tuỳ cấu hình) dịch vụ OTP gửi mã 6 số tới
 * chính email đó.
 */
@Composable
fun GoogleAuthScreen(
    currentUser: UserProfile?,
    onSignInSuccess: (UserProfile) -> Unit,
    onGuestLogin: () -> Unit,
    onProceedToApp: () -> Unit = onGuestLogin,
    onSignOut: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onGoogleSignIn: suspend () -> GoogleSignInResult = {
        GoogleSignInResult.Failed("Google Sign-In is not connected.")
    },
    requireOtp: Boolean = true,
    otpService: OtpService? = null,
    otpConfigError: String? = null
) {
    val strings = LocalStrings.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var authStep by remember { mutableStateOf(GoogleAuthStep.NONE) }
    var pendingAccount by remember { mutableStateOf<GoogleAccount?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var resendCountdown by remember { mutableIntStateOf(0) }
    var isSigningIn by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    // Máy không có tài khoản Google nào dùng được: không tự chuyển sang Khách (sẽ che
    // lỗi cấu hình SHA-1/package), chỉ làm nổi bật nút Khách để người dùng chủ động chọn.
    var noCredential by remember { mutableStateOf(false) }

    LaunchedEffect(authStep, resendCountdown) {
        if (authStep == GoogleAuthStep.VERIFY_OTP && resendCountdown > 0) {
            kotlinx.coroutines.delay(1000L)
            resendCountdown -= 1
        }
    }

    fun sendOtp() {
        val account = pendingAccount ?: return
        val service = otpService
        if (service == null) {
            otpError = otpConfigError ?: strings.otpNotConfigured
            return
        }
        scope.launch {
            isSendingOtp = true
            otpError = null
            when (val result = service.requestCode(account.email)) {
                OtpResult.Sent -> resendCountdown = 60
                is OtpResult.Failed -> otpError = result.message
                OtpResult.Verified -> Unit
            }
            isSendingOtp = false
        }
    }

    fun startGoogleSignIn() {
        scope.launch {
            isSigningIn = true
            signInError = null
            noCredential = false
            when (val result = onGoogleSignIn()) {
                is GoogleSignInResult.Success -> {
                    if (requireOtp) {
                        pendingAccount = result.account
                        otpInput = ""
                        otpError = null
                        authStep = GoogleAuthStep.VERIFY_OTP
                        sendOtp()
                    } else {
                        // Google đã xác thực chủ tài khoản và ký ID token, nên đăng nhập
                        // được ngay. Không phụ thuộc dịch vụ gửi mail, không giới hạn
                        // số người đăng nhập — phù hợp khi demo cho nhiều tài khoản.
                        onSignInSuccess(result.account.toUserProfile())
                    }
                }
                // Người dùng tự đóng bảng chọn: ở lại màn hình đăng nhập, không làm gì.
                GoogleSignInResult.Cancelled -> Unit
                is GoogleSignInResult.Failed -> {
                    signInError = result.message
                    noCredential = result.noCredential
                }
            }
            isSigningIn = false
        }
    }

    fun verifyAndCompleteLogin() {
        val account = pendingAccount ?: return
        val service = otpService
        if (service == null) {
            otpError = otpConfigError ?: strings.otpNotConfigured
            return
        }
        val code = otpInput.trim()
        if (code.length != 6) {
            otpError = strings.otpLengthError
            return
        }
        scope.launch {
            isVerifying = true
            otpError = null
            when (val result = service.verifyCode(account.email, code)) {
                OtpResult.Verified -> {
                    authStep = GoogleAuthStep.NONE
                    onSignInSuccess(account.toUserProfile())
                }
                is OtpResult.Failed -> otpError = result.message
                OtpResult.Sent -> Unit
            }
            isVerifying = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(MixtapeTokens.Space4)
            .testTag("screen_auth")
    ) {
        if (onBack != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = MixtapeTokens.TextPrimary)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).background(MixtapeTokens.AccentPrimary, CircleShape)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(strings.appName, style = MixtapeTypography.displayLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold))
        Text(strings.authTagline, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextSecondary)

        Spacer(modifier = Modifier.height(28.dp))

        if (currentUser?.isLoggedIn == true) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MixtapeTokens.StateSuccess, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.accountVerified, style = MixtapeTypography.titleMedium, color = MixtapeTokens.StateSuccess)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(currentUser.displayName, style = MixtapeTypography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(currentUser.email, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextSecondary)

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onProceedToApp,
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_proceed_button")
                    ) {
                        Text(strings.enterApp, style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { startGoogleSignIn() }, enabled = !isSigningIn) {
                            Text(strings.switchAccount, color = MixtapeTokens.AccentPrimary, style = MixtapeTypography.labelMedium)
                        }
                        if (onSignOut != null) {
                            Spacer(modifier = Modifier.width(16.dp))
                            TextButton(onClick = onSignOut) {
                                Text(strings.signOutAction, color = MixtapeTokens.StateWarn, style = MixtapeTypography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // Action 1: đăng nhập Google thật (Credential Manager, OTP tuỳ cấu hình).
            Button(
                onClick = { startGoogleSignIn() },
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_google_button")
                    .semantics { contentDescription = strings.continueWithGoogle }
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.openingGoogle, style = MixtapeTypography.bodyLarge, color = Color.White)
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.continueWithGoogle, style = MixtapeTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }

            if (signInError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgRaised),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("auth_sign_in_error")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MixtapeTokens.StateWarn, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = signInError!!,
                            style = MixtapeTypography.labelSmall,
                            color = MixtapeTokens.StateWarn
                        )
                    }
                }
            }

            // Cảnh báo cấu hình: hiện trước khi người dùng bấm, để không phải đoán
            // vì sao không có email nào tới hộp thư.
            if (requireOtp && otpConfigError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgRaised),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("auth_otp_config_warning")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MixtapeTokens.StateWarn, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.otpConfigWarning.format(otpConfigError),
                            style = MixtapeTypography.labelSmall,
                            color = MixtapeTokens.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 2: chế độ Khách (dùng offline). Khi máy không có tài khoản Google,
            // nút này được làm nổi bật để người dùng thấy ngay lối đi tiếp — nhưng vẫn
            // phải tự bấm, app không tự chuyển.
            OutlinedButton(
                onClick = onGuestLogin,
                shape = RoundedCornerShape(24.dp),
                colors = if (noCredential) {
                    ButtonDefaults.outlinedButtonColors(contentColor = MixtapeTokens.AccentPrimary)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = MixtapeTokens.TextSecondary)
                },
                border = BorderStroke(
                    width = if (noCredential) 1.5.dp else 1.dp,
                    color = if (noCredential) MixtapeTokens.AccentPrimary else MixtapeTokens.BgRaised
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_guest_button")
                    .semantics { contentDescription = strings.guestMode }
            ) {
                Text(
                    text = strings.guestMode,
                    color = if (noCredential) MixtapeTokens.AccentPrimary else MixtapeTokens.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (requireOtp) strings.privacyOtp else strings.privacyGoogle,
                        style = MixtapeTypography.bodySmall,
                        color = MixtapeTokens.TextSecondary
                    )
                }
            }
        }
    }

    // DIALOG BƯỚC 2: NHẬP VÀ XÁC THỰC MÃ OTP 6 CHỮ SỐ
    if (authStep == GoogleAuthStep.VERIFY_OTP) {
        Dialog(onDismissRequest = { authStep = GoogleAuthStep.NONE }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp).background(MixtapeTokens.AccentPrimary.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.otpTitle, style = MixtapeTypography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isSendingOtp) strings.otpSendingTo else strings.otpSentTo,
                        style = MixtapeTypography.bodySmall,
                        color = MixtapeTokens.TextSecondary
                    )
                    Text(
                        text = pendingAccount?.email.orEmpty(),
                        style = MixtapeTypography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MixtapeTokens.AccentPrimary
                    )

                    if (isSendingOtp) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MixtapeTokens.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.sendingEmail, style = MixtapeTypography.labelSmall, color = MixtapeTokens.TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6 ô số hiển thị trực quan
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 0 until 6) {
                            val digit = otpInput.getOrNull(i)?.toString() ?: ""
                            val isFocused = otpInput.length == i
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(MixtapeTokens.BgRaised, RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) MixtapeTokens.AccentPrimary else MixtapeTokens.BgRaised,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Text(
                                    text = digit,
                                    style = MixtapeTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MixtapeTokens.TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // TextField ẩn/nhập số 6 chữ số
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                otpInput = it
                                otpError = null
                            }
                        },
                        placeholder = { Text(strings.otpPlaceholder, style = MixtapeTypography.bodySmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = otpError != null,
                        supportingText = if (otpError != null) { { Text(otpError!!, color = MixtapeTokens.StateWarn) } } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bộ đếm đếm ngược và nút gửi lại
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (resendCountdown > 0) {
                            Text(
                                text = strings.resendIn.format(resendCountdown),
                                style = MixtapeTypography.labelSmall,
                                color = MixtapeTokens.TextSecondary
                            )
                        } else {
                            TextButton(
                                onClick = { sendOtp() },
                                enabled = !isSendingOtp
                            ) {
                                Text(strings.resend, color = MixtapeTokens.AccentPrimary, style = MixtapeTypography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { authStep = GoogleAuthStep.NONE }) {
                            Text(strings.close, color = MixtapeTokens.TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { verifyAndCompleteLogin() },
                            enabled = otpInput.length == 6 && !isVerifying && !isSendingOtp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MixtapeTokens.AccentPrimary,
                                disabledContainerColor = MixtapeTokens.BgRaised
                            ),
                            modifier = Modifier.testTag("otp_confirm_button")
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.checking, color = Color.White)
                            } else {
                                Text(strings.confirmSignIn, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.createPlaylistTitle, style = MixtapeTypography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(strings.playlistName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel, color = MixtapeTokens.TextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onCreate(name) },
                        colors = ButtonDefaults.buttonColors(containerColor = MixtapeTokens.AccentPrimary)
                    ) {
                        Text(strings.create, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MixtapeTokens.TextDisabled, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MixtapeTypography.titleLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextSecondary, textAlign = TextAlign.Center)
    }
}
