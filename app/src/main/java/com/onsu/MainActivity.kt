package com.onsu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onsu.data.download.DownloadOutcome
import com.onsu.data.models.PlaybackSession
import com.onsu.data.auth.AuthConfig
import com.onsu.data.auth.GoogleAuthManager
import com.onsu.data.auth.OtpServiceSetup
import com.onsu.data.auth.OtpServices
import com.onsu.data.models.Playlist
import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.data.models.UserProfile
import com.onsu.data.network.NetworkMonitor
import com.onsu.data.repository.MusicRepository
import com.onsu.data.scanner.LocalMusicScanner
import com.onsu.i18n.AppLanguage
import com.onsu.i18n.AppLocale
import com.onsu.i18n.LocalStrings
import com.onsu.i18n.appStrings
import com.onsu.player.PlayerConnection
import com.onsu.scoring.QueueManager
import com.onsu.settings.AppPreferences
import com.onsu.ui.DownloadUiState
import com.onsu.ui.components.AddToPlaylistDialog
import com.onsu.ui.components.MiniPlayer
import com.onsu.ui.components.NowPlayingSheet
import com.onsu.ui.screens.GoogleAuthScreen
import com.onsu.ui.screens.LibraryScreen
import com.onsu.ui.screens.LocalMusicScreen
import com.onsu.ui.screens.MainAutoPlaylistScreen
import com.onsu.ui.screens.OnboardingFolderGuideScreen
import com.onsu.ui.screens.OnboardingSeedsScreen
import com.onsu.ui.screens.PlaylistDetailScreen
import com.onsu.ui.screens.ProfileScreen
import com.onsu.ui.screens.SearchScreen
import com.onsu.ui.screens.SettingsScreen
import com.onsu.ui.theme.AppThemeMode
import com.onsu.ui.theme.MixtapeTheme
import com.onsu.ui.theme.MixtapeTokens
import com.onsu.ui.theme.MixtapeTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppScreen {
    ONBOARDING_SEEDS,
    ONBOARDING_FOLDER,
    MAIN,
    LIBRARY,
    PLAYLIST_DETAIL,
    SEARCH,
    LOCAL_MUSIC,
    SETTINGS,
    PROFILE,
    AUTH
}

class MainActivity : ComponentActivity() {
    /**
     * Scope sống ngoài vòng đời composition.
     *
     * Trước đây việc lưu phiên nghe dùng `rememberCoroutineScope()`, mà scope đó bị huỷ
     * đúng lúc composition bị dispose — nên lệnh lưu gần như không bao giờ chạy xong.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var preferences: AppPreferences
    private var themeMode by mutableStateOf(AppThemeMode.DARK)
    private var language by mutableStateOf(AppLanguage.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = AppPreferences(this)
        themeMode = preferences.themeMode
        language = preferences.language
        AppLocale.language = language

        enableEdgeToEdge()
        setContent {
            MixtapeTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalStrings provides appStrings(language)) {
                    MainAppHost(
                        appScope = appScope,
                        themeMode = themeMode,
                        language = language,
                        onThemeChange = { selected ->
                            themeMode = selected
                            preferences.themeMode = selected
                        },
                        onLanguageChange = { selected ->
                            language = selected
                            preferences.language = selected
                            AppLocale.language = selected
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appScope.cancel()
    }
}

@Composable
fun MainAppHost(
    appScope: CoroutineScope,
    themeMode: AppThemeMode,
    language: AppLanguage,
    onThemeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    val repository = remember { MusicRepository(context.applicationContext) }
    val queueManager = remember { QueueManager() }
    val playerConnection = remember { PlayerConnection(context, queueManager, scope) }

    // Xác thực thật: Credential Manager cho Google, dịch vụ ngoài cho mã 6 số.
    // Cả hai đều đọc cấu hình từ .env; thiếu cấu hình thì báo lỗi chứ không giả lập.
    // Phải truyền Activity context (LocalContext trong setContent chính là Activity),
    // vì Credential Manager cần một Activity để mở bảng chọn tài khoản hệ thống.
    val authManager = remember { GoogleAuthManager(context, repository) }
    val otpSetup = remember {
        if (AuthConfig.requireOtp) OtpServices.create() else OtpServiceSetup(null, null)
    }

    var currentScreen by remember { mutableStateOf(AppScreen.AUTH) }
    var selectedPlaylistDetail by remember { mutableStateOf<Playlist?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isNowPlayingOpen by remember { mutableStateOf(false) }

    var selectedSeedGenres by remember { mutableStateOf(setOf<String>()) }
    var localMusicList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recommendationPool by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isRefreshingRecommendations by remember { mutableStateOf(false) }
    var canLoadMoreRecommendations by remember { mutableStateOf(true) }
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember { mutableStateOf(LocalMusicScanner.hasAudioPermission(context)) }
    var isScanningLocal by remember { mutableStateOf(false) }
    var localScanError by remember { mutableStateOf<String?>(null) }
    var catalogReady by remember { mutableStateOf(false) }
    var localRefreshPending by remember { mutableStateOf(false) }

    // Gợi ý là nội dung online: không có mạng thì màn hình phải nói rõ và để trống,
    // không được lấp bằng kho nhạc dự phòng (người dùng thấy đó là "đề xuất bừa bãi").
    var isOffline by remember { mutableStateOf(!NetworkMonitor.isOnline(context)) }

    val activeQueue by queueManager.queueState.collectAsState()
    val activeStyle by queueManager.activeStyle.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentPositionMs by playerConnection.currentPositionMs.collectAsState()
    val durationMs by playerConnection.durationMs.collectAsState()
    val playbackError by playerConnection.playbackError.collectAsState()

    val likedTracks by repository.likedTracksFlow.collectAsState(initial = emptyList())
    val likedIds = remember(likedTracks) { likedTracks.map { it.id }.toSet() }
    val playlists by repository.playlistsFlow.collectAsState(initial = emptyList())
    val currentUser by repository.userProfileFlow.collectAsState(initial = UserProfile())

    // Tải nhạc: danh sách bài đã có trên máy + tiến độ các bài đang tải.
    // Bộ đếm tiến độ nằm ngoài danh sách tải vì nó thay đổi liên tục trong lúc tải.
    val downloadedTracks by repository.downloadedTracksFlow.collectAsState(initial = emptyList())
    var downloadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val downloadState = remember(downloadedTracks, downloadProgress) {
        DownloadUiState(
            downloadedIds = downloadedTracks.mapTo(HashSet()) { it.id },
            progress = downloadProgress
        )
    }

    // Search runs across everything the user owns, including device files.
    val searchPool = remember(localMusicList, recommendationPool) {
        (localMusicList + recommendationPool).distinctBy { it.id }
    }

    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(likedIds) {
        queueManager.syncLikedTracks(likedIds)
    }

    LaunchedEffect(playbackError) {
        playbackError?.let {
            toastMessage = it
            // Nhạc ngưng vì mất mạng: đưa màn hình về đúng trạng thái offline thay vì
            // để danh sách gợi ý cũ nằm đó như thể vẫn dùng được.
            if (!NetworkMonitor.isOnline(context)) isOffline = true
        }
    }

    fun toggleLikeTrack(track: Track) {
        val isLiked = likedIds.contains(track.id)
        scope.launch {
            if (isLiked) {
                repository.removeLikedTrack(track.id)
                queueManager.recordLike(track.id, false)
            } else {
                repository.saveLikedTrack(track)
                queueManager.recordLike(track.id, true)
            }
        }
    }

    /**
     * Nút tải xuống là công tắc hai chiều: chưa có thì tải về, đã có thì xoá bản tải.
     *
     * Tệp được đưa vào thư mục `Music/Onsu` của máy (MediaStore), nên người dùng thấy
     * bài trong ứng dụng quản lý tệp và nghe được bằng app nhạc khác, không chỉ trong
     * app này.
     */
    fun toggleDownload(track: Track) {
        if (track.source == TrackSource.LOCAL) return
        if (downloadState.isDownloading(track.id)) return

        if (downloadState.isDownloaded(track.id)) {
            scope.launch {
                val deleted = repository.deleteDownload(track.id)
                toastMessage = if (deleted) {
                    strings.deletedDownload.format(track.title)
                } else {
                    strings.deleteDownloadFailed.format(track.title)
                }
            }
            return
        }

        downloadProgress = downloadProgress + (track.id to 0f)
        scope.launch {
            val outcome = repository.downloadTrack(track) { value ->
                // Callback chạy trên luồng IO; đẩy cập nhật tiến độ về main thread.
                scope.launch { downloadProgress = downloadProgress + (track.id to value) }
            }
            downloadProgress = downloadProgress - track.id
            toastMessage = when (outcome) {
                is DownloadOutcome.Success -> {
                    // Định dạng riêng phần số: tên bài có thể chứa '%' và làm hỏng format.
                    val sizeMb = "%.1f".format(outcome.fileSizeBytes.toDouble() / (1024 * 1024))
                    strings.savedDownload.format(track.title, sizeMb)
                }
                is DownloadOutcome.Failure -> outcome.message
            }
        }
    }

    fun proceedAfterAuth() {
        if (selectedSeedGenres.isNotEmpty()) {
            currentScreen = AppScreen.MAIN
        } else {
            currentScreen = AppScreen.ONBOARDING_SEEDS
        }
    }

    /**
     * Xoá mọi trạng thái gắn với tài khoản đang dùng, cả trong bộ nhớ.
     *
     * Dữ liệu thư viện đã tách theo `ownerId` ở repository, nhưng bộ chấm điểm vẫn giữ
     * like/skip/lịch sử nghe trong RAM — không xoá thì tài khoản mới vẫn bị chấm điểm
     * theo hành vi của người trước dù DB đã đúng.
     */
    fun resetAccountSessionState() {
        queueManager.likedTrackIds.clear()
        queueManager.completedTrackIds.clear()
        queueManager.skippedTrackIds.clear()
        queueManager.playHistoryTimestamps.clear()
        queueManager.clearQueue()
        playerConnection.stopAndClear()
        selectedPlaylistDetail = null
        isNowPlayingOpen = false
    }

    /**
     * Chuyển sang tài khoản vừa đăng nhập: dựng lại hàng đợi theo gu của người đó.
     *
     * Tài khoản chưa có gu nhạc thì đưa thẳng vào onboarding — không kế thừa gu của
     * người đăng nhập trước.
     */
    fun applyAccountChange() {
        resetAccountSessionState()
        scope.launch {
            val seeds = repository.getTasteSeeds()
            selectedSeedGenres = seeds.second

            val pool = queueManager.candidatePool.ifEmpty { repository.loadRecommendationPool() }
            queueManager.updateCandidatePool(pool)

            if (seeds.second.isEmpty()) {
                currentScreen = AppScreen.ONBOARDING_SEEDS
                return@launch
            }

            queueManager.initialize(pool, emptySet(), seeds.second)
            val newQueue = queueManager.queueState.value
            if (newQueue.isNotEmpty()) {
                playerConnection.setQueue(newQueue, 0, 0L, autoPlay = false)
            }
            canLoadMoreRecommendations = true
            currentScreen = AppScreen.MAIN
        }
    }

    // Trước đây chỗ này là một LaunchedEffect theo dõi seed. Nó khiến onboarding tự
    // nhảy vào màn nghe nhạc ngay khi người dùng chọn ô seed đầu tiên — vì điều kiện
    // "đã đăng nhập + có seed" đúng ngay giữa lúc đang chọn, còn queue thì chưa hề
    // được dựng nên màn hình trắng/đen. Việc vào thẳng MAIN giờ chỉ xảy ra một lần,
    // trong lúc khôi phục phiên cũ (xem khối LaunchedEffect bên dưới).

    val playlistTracks by remember(selectedPlaylistDetail) {
        if (selectedPlaylistDetail != null) repository.getTracksForPlaylist(selectedPlaylistDetail!!.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    fun refreshLocalAudio() {
        // A permission result can arrive while the old session is still restoring.
        // Defer it so startup cannot overwrite a newly refreshed candidate pool.
        if (!catalogReady) {
            localRefreshPending = true
            return
        }
        if (isScanningLocal) {
            localRefreshPending = true
            return
        }
        isScanningLocal = true
        localScanError = null
        scope.launch {
            try {
                // Scanning the device only refreshes the local library screen. Device
                // audio is never part of the recommendation pool, so this must not
                // rebuild the queue the user is listening to.
                val localTracks = repository.loadLocalTracks(forceRefresh = true)
                hasAudioPermission = LocalMusicScanner.hasAudioPermission(context)
                localMusicList = localTracks
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("OnsuLocalMusic", "Cannot refresh local library", error)
                localScanError = strings.localScanError
            } finally {
                isScanningLocal = false
                if (localRefreshPending && coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                    localRefreshPending = false
                    refreshLocalAudio()
                }
            }
        }
    }

    /**
     * Pull-to-refresh on the main screen: resample the online catalog into a fresh
     * 20-track page. The song currently playing is kept first so audio is not
     * interrupted by the reload.
     *
     * Không có mạng thì không gọi mạng luôn: chuyển sang trạng thái offline để màn
     * hình trống kèm thông báo, đúng như khi mở app lúc đang offline.
     */
    fun reloadRecommendations() {
        if (isRefreshingRecommendations) return
        if (!NetworkMonitor.isOnline(context)) {
            isOffline = true
            return
        }
        isRefreshingRecommendations = true
        scope.launch {
            try {
                // Keep the indicator on screen long enough to read as a real action.
                kotlinx.coroutines.delay(350)
                if (queueManager.candidatePool.isEmpty()) {
                    val pool = repository.loadRecommendationPool()
                    recommendationPool = pool
                    queueManager.updateCandidatePool(pool)
                }
                if (queueManager.candidatePool.isEmpty()) {
                    // Không lấy được bài nào: coi như đang offline, không dựng gợi ý rác.
                    isOffline = true
                    return@launch
                }

                // Mở app lúc offline rồi mới có mạng: hàng đợi chưa từng được dựng, nên
                // phải khởi tạo theo gu đã chọn thay vì chỉ làm mới danh sách rỗng.
                if (queueManager.queueState.value.isEmpty() && selectedSeedGenres.isNotEmpty()) {
                    queueManager.initialize(
                        pool = queueManager.candidatePool,
                        artists = emptySet(),
                        genres = selectedSeedGenres
                    )
                    val rebuilt = queueManager.queueState.value
                    if (rebuilt.isNotEmpty()) {
                        playerConnection.setQueue(rebuilt, 0, 0L, autoPlay = false)
                    }
                    canLoadMoreRecommendations = rebuilt.isNotEmpty()
                    isOffline = false
                    return@launch
                }

                isOffline = false
                val refreshed = queueManager.refreshRecommendations()
                canLoadMoreRecommendations = refreshed.isNotEmpty()
            } catch (error: CancellationException) {
                throw error
            } finally {
                isRefreshingRecommendations = false
            }
        }
    }

    // Có mạng trở lại thì tự nạp gợi ý, để người dùng không phải kéo làm mới lần nữa.
    DisposableEffect(Unit) {
        val callback = NetworkMonitor.observe(
            context,
            onAvailable = {
                // Callback chạy trên luồng của hệ thống; scope của composition là main.
                scope.launch { if (isOffline) reloadRecommendations() }
            }
        )
        onDispose { callback?.let { NetworkMonitor.stopObserving(context, it) } }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAudioPermission = LocalMusicScanner.hasAudioPermission(context)
        refreshLocalAudio()
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.LOCAL_MUSIC) refreshLocalAudio()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (!hasAudioPermission) permissionsToRequest.add(audioPermission)
        // Android 9 trở xuống không có scoped storage: muốn ghi bài đã tải vào thư mục
        // Music của máy thì phải có quyền ghi bộ nhớ.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) permissionLauncher.launch(permissionsToRequest.toTypedArray())

        // 1. Connect to Media3 Service
        playerConnection.connectAsync {
            scope.launch {
                // 2. Restore seeds, pool, likes and last session
                // Nạp bảng tra cứu tệp đã tải trước khi dựng queue, để bài tải sẵn được
                // phát từ đĩa ngay từ lần mở app đầu tiên (kể cả khi đang không có mạng).
                try {
                    repository.refreshDownloadRegistry()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("OnsuDownloads", "Cannot index downloaded tracks", error)
                }

                val seeds = repository.getTasteSeeds()
                selectedSeedGenres = seeds.second

                // Offline ngay từ đầu: bỏ hẳn bước lấy catalog. Gọi mạng lúc này chỉ
                // trả về rỗng rồi bị lấp bằng nhạc dự phòng, tạo ra gợi ý vô nghĩa.
                val online = NetworkMonitor.isOnline(context)
                if (!online) isOffline = true

                val pool = if (!online) {
                    emptyList()
                } else {
                    try {
                        repository.loadRecommendationPool()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // loadRecommendationPool already falls back to the bundled catalog,
                        // so this only happens on a programming error — never surface it as
                        // a device-library problem.
                        Log.e("OnsuRecommendations", "Cannot load the online catalog", error)
                        emptyList()
                    }
                }
                recommendationPool = pool
                hasAudioPermission = LocalMusicScanner.hasAudioPermission(context)
                localMusicList = try {
                    repository.loadLocalTracks()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("OnsuLocalMusic", "Cannot scan device audio", error)
                    emptyList()
                }
                queueManager.updateCandidatePool(pool)
                canLoadMoreRecommendations = pool.isNotEmpty()

                val storedLikes = repository.likedTracksFlow.first().map { it.id }.toSet()
                val restoredSession = repository.restorePlaybackSession()

                // Local files are still resolvable here so a queue the service is
                // already playing keeps working after the Activity is recreated.
                val adoptedServiceQueue = playerConnection.adoptExistingQueue(pool + localMusicList)
                if (!adoptedServiceQueue && pool.isNotEmpty() && seeds.second.isNotEmpty()) {
                    // Nạp like trước khi dựng queue để điểm số lần đầu đã tính tới chúng.
                    queueManager.likedTrackIds.clear()
                    queueManager.likedTrackIds.addAll(storedLikes)

                    queueManager.initialize(
                        pool = pool,
                        artists = emptySet(),
                        genres = seeds.second,
                        style = restoredSession?.playlistMode ?: TasteLevel.BALANCED,
                        restoredTrackId = restoredSession?.trackId
                    )

                    // 3. Đưa queue lên player (không tự phát) và khôi phục đúng vị trí đã nghe dở.
                    val restoredQueue = queueManager.queueState.value
                    if (restoredQueue.isNotEmpty()) {
                        playerConnection.setQueue(
                            tracks = restoredQueue,
                            startIndex = queueManager.currentIndex.value,
                            initialPositionMs = if (queueManager.currentTrack?.id == restoredSession?.trackId)
                                restoredSession?.positionMs ?: 0L else 0L,
                            autoPlay = false
                        )
                    }
                }

                // 4. Gắn cầu nối queue → player sau khi queue ban đầu đã dựng xong.
                playerConnection.attachQueue()

                // 5. Chỉ ở lần khôi phục này mới được phép bỏ qua màn hình đăng nhập:
                //    đã có hồ sơ đăng nhập thì vào thẳng app; nếu chưa chọn gu nhạc thì
                //    vào onboarding. Chưa có hồ sơ thì giữ nguyên màn hình đăng nhập.
                val storedUser = repository.userProfileFlow.first()
                if (storedUser?.isLoggedIn == true && currentScreen == AppScreen.AUTH) {
                    currentScreen =
                        if (seeds.second.isNotEmpty()) AppScreen.MAIN
                        else AppScreen.ONBOARDING_SEEDS
                }
                catalogReady = true
                if (localRefreshPending) {
                    localRefreshPending = false
                    refreshLocalAudio()
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Chụp lại phiên nghe hiện tại. Phải đọc state trên main thread rồi mới launch,
    // vì queue và player đều chỉ được đọc an toàn từ main thread.
    fun persistSession() {
        val track = queueManager.currentTrack ?: return
        val snapshot = PlaybackSession(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            streamUrl = track.audioUrl,
            source = track.source,
            sourceId = track.sourceId,
            durationMs = track.durationMs,
            genre = track.genre,
            positionMs = playerConnection.currentPositionMs.value,
            playlistMode = queueManager.activeStyle.value
        )
        appScope.launch { repository.savePlaybackSession(snapshot) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Lưu khi app bị đẩy xuống nền — đây mới là thời điểm chắc chắn còn cơ hội ghi DB.
            if (event == Lifecycle.Event.ON_STOP) persistSession()
            if (event == Lifecycle.Event.ON_RESUME) {
                // Quay lại app sau khi mất mạng: màn gợi ý phải hiện thông báo offline
                // ngay, giống như khi thoát ra vào lại.
                if (!NetworkMonitor.isOnline(context)) isOffline = true
                val granted = LocalMusicScanner.hasAudioPermission(context)
                if (granted != hasAudioPermission || currentScreen == AppScreen.LOCAL_MUSIC) {
                    hasAudioPermission = granted
                    refreshLocalAudio()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistSession()
            playerConnection.release()
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.MAIN && currentScreen != AppScreen.AUTH) {
        when (currentScreen) {
            AppScreen.ONBOARDING_SEEDS -> currentScreen = AppScreen.AUTH
            AppScreen.ONBOARDING_FOLDER -> currentScreen = AppScreen.ONBOARDING_SEEDS
            AppScreen.PLAYLIST_DETAIL -> currentScreen = AppScreen.LIBRARY
            AppScreen.LOCAL_MUSIC -> currentScreen = AppScreen.SETTINGS
            AppScreen.PROFILE -> currentScreen = AppScreen.SETTINGS
            else -> currentScreen = AppScreen.MAIN
        }
    }

    Scaffold(
        containerColor = MixtapeTokens.BgBase,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (currentScreen != AppScreen.ONBOARDING_SEEDS && currentScreen != AppScreen.ONBOARDING_FOLDER && currentScreen != AppScreen.AUTH) {
                Column(modifier = Modifier.fillMaxWidth().background(MixtapeTokens.BgSurface)) {
                    val curTrack = queueManager.currentTrack?.let { it.copy(isLiked = likedIds.contains(it.id)) }
                    if (curTrack != null) {
                        MiniPlayer(
                            track = curTrack,
                            isPlaying = isPlaying,
                            currentPosMs = currentPositionMs,
                            durationMs = durationMs,
                            onPlayPause = { playerConnection.togglePlayPause() },
                            onNext = { playerConnection.skipNext() },
                            onOpenNowPlaying = { isNowPlayingOpen = true }
                        )
                    }

                    NavigationBar(
                        containerColor = MixtapeTokens.BgSurface,
                        // Không đặt height cứng: NavigationBar tự cộng thêm inset của thanh
                        // điều hướng hệ thống. Trước đây height(56.dp) làm inset ăn vào phần
                        // thân, khiến nhãn tab bị thanh gesture che mất.
                        modifier = Modifier.fillMaxWidth().testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.MAIN,
                            onClick = { currentScreen = AppScreen.MAIN },
                            icon = { Icon(Icons.Default.Explore, contentDescription = strings.navDiscover) },
                            label = { Text(strings.navDiscover, style = MixtapeTypography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MixtapeTokens.AccentPrimary,
                                selectedTextColor = MixtapeTokens.AccentPrimary,
                                unselectedIconColor = MixtapeTokens.TextSecondary,
                                unselectedTextColor = MixtapeTokens.TextSecondary,
                                indicatorColor = MixtapeTokens.BgRaised
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.LIBRARY || currentScreen == AppScreen.PLAYLIST_DETAIL,
                            onClick = { currentScreen = AppScreen.LIBRARY },
                            icon = { Icon(Icons.Default.QueueMusic, contentDescription = strings.navLibrary) },
                            label = { Text(strings.navLibrary, style = MixtapeTypography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MixtapeTokens.AccentPrimary,
                                selectedTextColor = MixtapeTokens.AccentPrimary,
                                unselectedIconColor = MixtapeTokens.TextSecondary,
                                unselectedTextColor = MixtapeTokens.TextSecondary,
                                indicatorColor = MixtapeTokens.BgRaised
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.SEARCH,
                            onClick = { currentScreen = AppScreen.SEARCH },
                            icon = { Icon(Icons.Default.Search, contentDescription = strings.navSearch) },
                            label = { Text(strings.navSearch, style = MixtapeTypography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MixtapeTokens.AccentPrimary,
                                selectedTextColor = MixtapeTokens.AccentPrimary,
                                unselectedIconColor = MixtapeTokens.TextSecondary,
                                unselectedTextColor = MixtapeTokens.TextSecondary,
                                indicatorColor = MixtapeTokens.BgRaised
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.SETTINGS ||
                                currentScreen == AppScreen.LOCAL_MUSIC ||
                                currentScreen == AppScreen.PROFILE,
                            onClick = { currentScreen = AppScreen.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = strings.navSettings) },
                            label = { Text(strings.navSettings, style = MixtapeTypography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MixtapeTokens.AccentPrimary,
                                selectedTextColor = MixtapeTokens.AccentPrimary,
                                unselectedIconColor = MixtapeTokens.TextSecondary,
                                unselectedTextColor = MixtapeTokens.TextSecondary,
                                indicatorColor = MixtapeTokens.BgRaised
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentScreen) {
                AppScreen.ONBOARDING_SEEDS -> OnboardingSeedsScreen(
                    selectedGenres = selectedSeedGenres,
                    canProceed = selectedSeedGenres.size in 3..5,
                    onToggleGenre = { g ->
                        selectedSeedGenres = if (selectedSeedGenres.contains(g)) selectedSeedGenres - g else selectedSeedGenres + g
                    },
                    onProceed = { currentScreen = AppScreen.ONBOARDING_FOLDER }
                )

                AppScreen.ONBOARDING_FOLDER -> OnboardingFolderGuideScreen(
                    onOpenFileManager = {
                        scope.launch {
                            repository.saveTasteSeeds(emptySet(), selectedSeedGenres)
                            val pool = repository.loadRecommendationPool()
                            recommendationPool = pool
                            queueManager.updateCandidatePool(pool)
                            queueManager.initialize(pool, emptySet(), selectedSeedGenres)
                            val tracks = queueManager.queueState.value
                            playerConnection.setQueueAndPlay(tracks, 0, 0L)
                            canLoadMoreRecommendations = true
                            currentScreen = AppScreen.MAIN
                        }
                    },
                    onSkip = {
                        scope.launch {
                            repository.saveTasteSeeds(emptySet(), selectedSeedGenres)
                            val pool = repository.loadRecommendationPool()
                            recommendationPool = pool
                            queueManager.updateCandidatePool(pool)
                            queueManager.initialize(pool, emptySet(), selectedSeedGenres)
                            val tracks = queueManager.queueState.value
                            playerConnection.setQueueAndPlay(tracks, 0, 0L)
                            canLoadMoreRecommendations = true
                            currentScreen = AppScreen.MAIN
                        }
                    }
                )

                AppScreen.MAIN -> MainAutoPlaylistScreen(
                    queue = activeQueue,
                    currentTrackId = queueManager.currentTrack?.id,
                    isPlaying = isPlaying,
                    activeStyle = activeStyle,
                    isRefreshing = isRefreshingRecommendations,
                    canLoadMore = canLoadMoreRecommendations,
                    isOffline = isOffline,
                    onRefresh = { reloadRecommendations() },
                    onLoadMore = {
                        // Appending a page is synchronous; a 0 result means the online
                        // catalog has been exhausted for this session.
                        if (queueManager.loadMore() == 0) canLoadMoreRecommendations = false
                    },
                    onSelectStyle = { newStyle ->
                        // Chỉ cần đổi gu trong queue; cầu nối sẽ tự thay phần đuôi của player.
                        queueManager.switchTasteStyle(newStyle)
                        canLoadMoreRecommendations = true
                    },
                    onPlayTrackIndex = { idx ->
                        queueManager.selectTrackIndex(idx)
                        playerConnection.playTrackAtIndex(idx)
                    },
                    onToggleLike = { track -> toggleLikeTrack(track) },
                    onToggleDownload = { track -> toggleDownload(track) },
                    downloadState = downloadState,
                    onAddToPlaylist = { track -> trackForPlaylistDialog = track },
                    onOpenSearch = { currentScreen = AppScreen.SEARCH },
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                )

                AppScreen.LIBRARY -> LibraryScreen(
                    likedTracks = likedTracks,
                    playlists = playlists,
                    downloadedTracks = downloadedTracks,
                    downloadState = downloadState,
                    currentTrackId = queueManager.currentTrack?.id,
                    isPlaying = isPlaying,
                    onPlayTrack = { track ->
                        val idx = activeQueue.indexOfFirst { it.id == track.id }
                        val downloadedIdx = downloadedTracks.indexOfFirst { it.id == track.id }
                        when {
                            idx >= 0 -> {
                                queueManager.selectTrackIndex(idx)
                                playerConnection.playTrackAtIndex(idx)
                            }
                            // Bài trong tab "Đã tải": phát cả danh sách đã tải để next/prev
                            // vẫn chạy, và tất cả đều đọc từ tệp cục bộ.
                            downloadedIdx >= 0 ->
                                playerConnection.playTracks(downloadedTracks, downloadedIdx)
                            else -> playerConnection.playTracks(listOf(track), 0)
                        }
                    },
                    onToggleLike = { track -> toggleLikeTrack(track) },
                    onToggleDownload = { track -> toggleDownload(track) },
                    onOpenPlaylist = { pl ->
                        selectedPlaylistDetail = pl
                        currentScreen = AppScreen.PLAYLIST_DETAIL
                    },
                    onCreatePlaylist = { title ->
                        scope.launch { repository.createPlaylist(title) }
                    },
                    onAddToPlaylist = { track -> trackForPlaylistDialog = track }
                )

                AppScreen.PLAYLIST_DETAIL -> PlaylistDetailScreen(
                    playlist = selectedPlaylistDetail,
                    tracks = playlistTracks,
                    currentTrackId = queueManager.currentTrack?.id,
                    isPlaying = isPlaying,
                    onBack = { currentScreen = AppScreen.LIBRARY },
                    onPlayTrack = { track ->
                        playerConnection.playTracks(playlistTracks, playlistTracks.indexOf(track))
                    },
                    onRemoveTrack = { trId ->
                        selectedPlaylistDetail?.let { pl ->
                            scope.launch { repository.removeTrackFromPlaylist(pl.id, trId) }
                        }
                    },
                    onToggleLike = { track -> toggleLikeTrack(track) },
                    downloadState = downloadState,
                    onToggleDownload = { track -> toggleDownload(track) }
                )

                AppScreen.SEARCH -> SearchScreen(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    // Search is an explicit user query, so device audio stays findable
                    // here even though it is never recommended automatically.
                    candidatePool = searchPool,
                    currentTrackId = queueManager.currentTrack?.id,
                    isPlaying = isPlaying,
                    onPlayTrack = { track ->
                        playerConnection.playTracks(listOf(track), 0)
                    },
                    onToggleLike = { track -> toggleLikeTrack(track) },
                    onAddToPlaylist = { track -> trackForPlaylistDialog = track },
                    downloadState = downloadState,
                    onToggleDownload = { track -> toggleDownload(track) }
                )

                AppScreen.LOCAL_MUSIC -> LocalMusicScreen(
                    localTracks = localMusicList,
                    currentTrackId = queueManager.currentTrack?.id,
                    isPlaying = isPlaying,
                    onPlayTrack = { track ->
                        val playable = localMusicList.filter { it.isPlayable }
                        val index = playable.indexOfFirst { it.id == track.id }
                        if (index >= 0) playerConnection.playTracks(playable, index)
                    },
                    onToggleLike = { track -> toggleLikeTrack(track) },
                    onAddToPlaylist = { track -> trackForPlaylistDialog = track },
                    onScanMusic = {
                        if (LocalMusicScanner.hasAudioPermission(context)) refreshLocalAudio()
                        else permissionLauncher.launch(arrayOf(audioPermission))
                    },
                    onBack = { currentScreen = AppScreen.SETTINGS },
                    hasAudioPermission = hasAudioPermission,
                    isScanning = isScanningLocal || !catalogReady,
                    errorMessage = localScanError ?: playbackError,
                    onOpenPermissionSettings = {
                        context.startActivity(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ))
                    }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    user = currentUser,
                    themeMode = themeMode,
                    language = language,
                    onSelectTheme = onThemeChange,
                    onSelectLanguage = onLanguageChange,
                    onOpenProfile = { currentScreen = AppScreen.PROFILE },
                    onOpenLocalMusic = { currentScreen = AppScreen.LOCAL_MUSIC },
                    onResetOnboarding = { currentScreen = AppScreen.ONBOARDING_SEEDS },
                    onSignOut = {
                        scope.launch {
                            authManager.signOut()
                            // Thư viện của tài khoản vừa đăng xuất vẫn nằm trong DB theo
                            // ownerId; chỉ cần bỏ trạng thái đang hiển thị và dừng nhạc.
                            resetAccountSessionState()
                            currentScreen = AppScreen.AUTH
                        }
                    }
                )

                AppScreen.PROFILE -> ProfileScreen(
                    user = currentUser,
                    onBack = { currentScreen = AppScreen.SETTINGS },
                    onSignIn = { currentScreen = AppScreen.AUTH },
                    onSignOut = {
                        scope.launch {
                            authManager.signOut()
                            resetAccountSessionState()
                            currentScreen = AppScreen.AUTH
                        }
                    }
                )

                AppScreen.AUTH -> GoogleAuthScreen(
                    currentUser = currentUser,
                    onGoogleSignIn = { authManager.signIn() },
                    requireOtp = AuthConfig.requireOtp,
                    otpService = otpSetup.service,
                    otpConfigError = otpSetup.error,
                    onSignInSuccess = { user ->
                        scope.launch {
                            authManager.saveProfile(user)
                            // Đổi tài khoản phải dựng lại hàng đợi theo gu của người mới,
                            // không giữ bài đang phát của người trước.
                            applyAccountChange()
                        }
                    },
                    onGuestLogin = {
                        scope.launch {
                            // Vào chế độ khách phải thực sự rời tài khoản đang đăng nhập,
                            // nếu không người dùng vẫn thấy thư viện của tài khoản đó.
                            repository.clearUser()
                            applyAccountChange()
                        }
                    },
                    onProceedToApp = { proceedAfterAuth() },
                    onSignOut = {
                        scope.launch {
                            authManager.signOut()
                            resetAccountSessionState()
                        }
                    },
                    onBack = { currentScreen = AppScreen.SETTINGS }
                )
            }

            // Now Playing Sheet
            if (isNowPlayingOpen && queueManager.currentTrack != null) {
                val curTrack = queueManager.currentTrack!!.let {
                    it.copy(
                        isLiked = likedIds.contains(it.id),
                        isDownloaded = downloadState.isDownloaded(it.id)
                    )
                }
                NowPlayingSheet(
                    track = curTrack,
                    isPlaying = isPlaying,
                    currentPosMs = currentPositionMs,
                    durationMs = durationMs,
                    playlists = playlists,
                    onDismiss = { isNowPlayingOpen = false },
                    onPlayPause = { playerConnection.togglePlayPause() },
                    onNext = { playerConnection.skipNext() },
                    onPrev = { playerConnection.skipPrevious() },
                    onSeek = { pos -> playerConnection.seekTo(pos) },
                    onToggleLike = { toggleLikeTrack(curTrack) },
                    onToggleDownload = { toggleDownload(curTrack) },
                    isDownloading = downloadState.isDownloading(curTrack.id),
                    downloadProgress = downloadState.progressOf(curTrack.id) ?: 0f,
                    onAddToPlaylist = { plId ->
                        scope.launch {
                            val pl = playlists.find { it.id == plId }
                            repository.addTrackToPlaylist(plId, curTrack)
                            toastMessage = strings.addedToPlaylist.format(curTrack.title, pl?.title ?: "playlist")
                        }
                    },
                    onCreatePlaylistAndAdd = { newName ->
                        scope.launch {
                            val newId = repository.createPlaylist(newName)
                            repository.addTrackToPlaylist(newId, curTrack)
                            toastMessage = strings.createdPlaylistAndAdded.format(newName)
                        }
                    }
                )
            }

            // Add To Playlist Dialog
            if (trackForPlaylistDialog != null) {
                val targetTrack = trackForPlaylistDialog!!
                AddToPlaylistDialog(
                    playlists = playlists,
                    onSelect = { plId ->
                        scope.launch {
                            val pl = playlists.find { it.id == plId }
                            repository.addTrackToPlaylist(plId, targetTrack)
                            toastMessage = strings.addedToPlaylist.format(targetTrack.title, pl?.title ?: "playlist")
                            trackForPlaylistDialog = null
                        }
                    },
                    onCreateAndSelect = { newName ->
                        scope.launch {
                            val newId = repository.createPlaylist(newName)
                            repository.addTrackToPlaylist(newId, targetTrack)
                            toastMessage = strings.createdPlaylistAndAdded.format(newName)
                            trackForPlaylistDialog = null
                        }
                    },
                    onDismiss = { trackForPlaylistDialog = null }
                )
            }

            // Feedback Toast Banner
            if (toastMessage != null) {
                LaunchedEffect(toastMessage) {
                    kotlinx.coroutines.delay(2200)
                    toastMessage = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MixtapeTokens.BgRaised),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MixtapeTokens.AccentPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(toastMessage!!, style = MixtapeTypography.bodyMedium, color = MixtapeTokens.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
