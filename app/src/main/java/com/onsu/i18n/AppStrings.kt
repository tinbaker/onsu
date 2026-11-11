package com.onsu.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.onsu.data.models.TasteLevel

/**
 * Ngôn ngữ giao diện. Mặc định là tiếng Anh vì catalog nhạc (Audius/Jamendo) chủ
 * yếu là tiếng Anh; tiếng Việt là tuỳ chọn trong Cài đặt.
 *
 * Không dùng locale hệ thống: người dùng có thể đặt máy tiếng Việt nhưng vẫn muốn
 * giao diện tiếng Anh (và ngược lại).
 */
enum class AppLanguage {
    ENGLISH,
    VIETNAMESE
}

/**
 * Ngôn ngữ hiện tại cho các lớp không phải Compose (scanner, downloader, player,
 * auth) — những nơi sinh ra thông báo lỗi hiển thị trực tiếp lên UI.
 *
 * Chỉ là một biến volatile: đổi ngôn ngữ sẽ cập nhật ngay ở lần tạo thông báo kế
 * tiếp; chuỗi đã tạo trước đó không đổi (chấp nhận được với thông báo lỗi ngắn).
 */
object AppLocale {
    @Volatile
    var language: AppLanguage = AppLanguage.ENGLISH
}

/** Chọn chuỗi theo ngôn ngữ đang dùng, dành cho lớp dữ liệu. */
fun tr(english: String, vietnamese: String): String =
    if (AppLocale.language == AppLanguage.VIETNAMESE) vietnamese else english

/**
 * Toàn bộ chuỗi hiển thị của app.
 *
 * Các chuỗi có tham số dùng `%s`/`%d` và được gọi bằng `.format(...)` tại chỗ.
 */
interface AppStrings {
    val appName: String

    // Điều hướng & hành động chung
    val navDiscover: String
    val navLibrary: String
    val navSearch: String
    val navSettings: String
    val back: String
    val close: String
    val cancel: String
    val create: String
    val retry: String
    val retrying: String
    val searchAction: String
    val settingsAction: String
    val pause: String
    val play: String
    val nextTrack: String
    val previousTrack: String
    val like: String
    val unlike: String
    val addToPlaylistAction: String
    val downloadAction: String
    val removeDownloadAction: String
    val unplayable: String
    val closePlayer: String
    val nowPlaying: String
    val onDeviceBadge: String

    // Gu nghe
    val tasteFamiliar: String
    val tasteBalanced: String
    val tasteExplore: String

    // Trang chính
    val offlineTitle: String
    val offlineBody: String
    val emptyQueueTitle: String
    val emptyQueueBody: String
    val reloadSuggestions: String
    val reloading: String
    val pagingMore: String
    val pagingEnd: String

    // Onboarding
    val stepLabel: String
    val stepCounter: String
    val seedsBubble: String
    val favoriteGenres: String
    val genresHint: String
    val selectedCount: String
    val continueLabel: String
    val selectAtLeastThree: String
    val folderBubble: String
    val folderStandardLocation: String
    val folderPutHere: String
    val folderNotVisible: String
    val startListening: String
    val later: String

    // Thư viện
    val libraryTitle: String
    val tabLiked: String
    val tabPlaylists: String
    val tabDownloads: String
    val emptyLikedTitle: String
    val emptyLikedBody: String
    val emptyPlaylistsTitle: String
    val emptyPlaylistsBody: String
    val createFirstPlaylist: String
    val privatePlaylist: String
    val playlistDetailTitle: String
    val playlistMeta: String
    val removeTrack: String
    val emptyDownloadsTitle: String
    val emptyDownloadsBody: String
    val downloadsSummary: String
    val downloadsLocation: String

    // Tìm kiếm
    val searchPlaceholder: String
    val searchEmptyTitle: String
    val searchEmptyBody: String

    // Nhạc trong máy
    val localTitle: String
    val localNoPermission: String
    val localScanning: String
    val localFound: String
    val scanAgain: String
    val localPermissionBody: String
    val grantAudioPermission: String
    val openAppSettings: String
    val localEmptyTitle: String
    val localEmptyBody: String

    // Cài đặt
    val settingsTitle: String
    val guestName: String
    val tapToSignIn: String
    val signIn: String
    val change: String
    val manageLocalMusic: String
    val preferencesLabel: String
    val languageLabel: String
    val languageEnglish: String
    val languageVietnamese: String
    val themeLabel: String
    val themeDark: String
    val themeLight: String
    val themeSystem: String
    val tasteSectionTitle: String
    val tasteSectionBody: String
    val redoOnboarding: String
    val signOutGoogle: String

    // Hồ sơ
    val profileTitle: String
    val accountLabel: String
    val accountGoogle: String
    val accountGuest: String
    val switchAccount: String
    val signOutAction: String
    val notSignedIn: String

    // Đăng nhập
    val authTagline: String
    val accountVerified: String
    val enterApp: String
    val continueWithGoogle: String
    val openingGoogle: String
    val guestMode: String
    val privacyOtp: String
    val privacyGoogle: String
    val otpTitle: String
    val otpSendingTo: String
    val otpSentTo: String
    val sendingEmail: String
    val otpPlaceholder: String
    val resendIn: String
    val resend: String
    val confirmSignIn: String
    val checking: String
    val otpLengthError: String
    val otpNotConfigured: String
    val otpConfigWarning: String

    // Hộp thoại
    val createPlaylistTitle: String
    val playlistName: String
    val addToPlaylistTitle: String
    val newPlaylist: String
    val newPlaylistName: String
    val playlistNameExample: String
    val createAndAdd: String
    val noPlaylists: String
    val createFirstPlaylistShort: String
    val deleteBrokenTitle: String
    val deleteBrokenBody: String
    val deleteForReal: String
    val trackUnplayableFallback: String

    // Thông báo ngắn
    val addedToPlaylist: String
    val createdPlaylistAndAdded: String
    val deletedDownload: String
    val deleteDownloadFailed: String
    val savedDownload: String
    val localScanError: String
}

object EnglishStrings : AppStrings {
    override val appName = "Onsu"

    override val navDiscover = "Discover"
    override val navLibrary = "Library"
    override val navSearch = "Search"
    override val navSettings = "Settings"
    override val back = "Back"
    override val close = "Close"
    override val cancel = "Cancel"
    override val create = "Create"
    override val retry = "Try again"
    override val retrying = "Retrying…"
    override val searchAction = "Search"
    override val settingsAction = "Settings"
    override val pause = "Pause"
    override val play = "Play"
    override val nextTrack = "Next track"
    override val previousTrack = "Previous track"
    override val like = "Like"
    override val unlike = "Unlike"
    override val addToPlaylistAction = "Add to playlist"
    override val downloadAction = "Download for offline listening"
    override val removeDownloadAction = "Downloaded, tap to remove"
    override val unplayable = "Can't play"
    override val closePlayer = "Close player"
    override val nowPlaying = "NOW PLAYING"
    override val onDeviceBadge = "ON DEVICE"

    override val tasteFamiliar = "Familiar"
    override val tasteBalanced = "Balanced"
    override val tasteExplore = "Explore"

    override val offlineTitle = "You're offline"
    override val offlineBody =
        "No network connection, so new suggestions can't load. Downloaded music is still available in Library → Downloads."
    override val emptyQueueTitle = "Nothing in the queue yet"
    override val emptyQueueBody =
        "Tap reload for fresh suggestions, or pick a different taste above."
    override val reloadSuggestions = "Reload suggestions"
    override val reloading = "Reloading…"
    override val pagingMore = "Scroll for 10 more tracks"
    override val pagingEnd = "No more suggestions this session · pull down to refresh"

    override val stepLabel = "Step %s"
    override val stepCounter = "1/2"
    override val seedsBubble =
        "Pick 3–5 genres you love — I'll build your queue around them."
    override val favoriteGenres = "Favorite genres"
    override val genresHint = "Pick at least 3 — you can change this later."
    override val selectedCount = "Selected: %s/5 (at least 3)"
    override val continueLabel = "Continue"
    override val selectAtLeastThree = "Pick at least 3"
    override val folderBubble =
        "Music you download only shows up if it sits in the Music/ folder."
    override val folderStandardLocation = "STANDARD FOLDER LOCATION ON ANDROID"
    override val folderPutHere = "← put it here"
    override val folderNotVisible = "not visible to Onsu"
    override val startListening = "Start listening"
    override val later = "Later"

    override val libraryTitle = "Library"
    override val tabLiked = "Liked (%s)"
    override val tabPlaylists = "Playlists (%s)"
    override val tabDownloads = "Downloads (%s)"
    override val emptyLikedTitle = "No liked tracks yet"
    override val emptyLikedBody = "Tap the heart on any track to save it here."
    override val emptyPlaylistsTitle = "No playlists yet"
    override val emptyPlaylistsBody =
        "Create your own playlist to organise the tracks you love."
    override val createFirstPlaylist = "Create your first playlist"
    override val privatePlaylist = "Private"
    override val playlistDetailTitle = "Personal playlist"
    override val playlistMeta = "%s tracks · 100%% private"
    override val removeTrack = "Remove"
    override val emptyDownloadsTitle = "Nothing downloaded yet"
    override val emptyDownloadsBody =
        "Tap the download icon on a suggestion to save it to Music/Onsu on this device."
    override val downloadsSummary = "%s tracks · %s MB · plays offline"
    override val downloadsLocation = "Files are stored in Music/Onsu on this device"

    override val searchPlaceholder = "Search songs and artists on device & online…"
    override val searchEmptyTitle = "Type something"
    override val searchEmptyBody =
        "Search across audio on your device and the open online catalog."

    override val localTitle = "On this device"
    override val localNoPermission = "Audio permission not granted"
    override val localScanning = "Scanning audio…"
    override val localFound = "%s audio files found"
    override val scanAgain = "Scan again"
    override val localPermissionBody =
        "Onsu needs permission to read audio so it can play files stored on this device."
    override val grantAudioPermission = "Grant audio permission"
    override val openAppSettings = "Open app settings"
    override val localEmptyTitle = "No audio found on this device"
    override val localEmptyBody =
        "Rescan after adding files to Music/, Download/ or a recordings folder. Other apps' private folders may not be accessible."

    override val settingsTitle = "Settings"
    override val guestName = "Guest"
    override val tapToSignIn = "Tap to sign in with Google"
    override val signIn = "Sign in"
    override val change = "Change"
    override val manageLocalMusic = "Manage music & audio on this device"
    override val preferencesLabel = "PREFERENCES"
    override val languageLabel = "Language"
    override val languageEnglish = "English"
    override val languageVietnamese = "Tiếng Việt"
    override val themeLabel = "Theme"
    override val themeDark = "Dark"
    override val themeLight = "Light"
    override val themeSystem = "System"
    override val tasteSectionTitle = "MUSIC TASTE"
    override val tasteSectionBody =
        "Change your listening style by picking 3–5 favourite genres again."
    override val redoOnboarding = "Redo onboarding"
    override val signOutGoogle = "Sign out of Google"

    override val profileTitle = "Profile"
    override val accountLabel = "ACCOUNT"
    override val accountGoogle = "Signed in with Google"
    override val accountGuest = "Guest mode — your library stays on this device"
    override val switchAccount = "Switch account"
    override val signOutAction = "Sign out"
    override val notSignedIn = "Not signed in"

    override val authTagline = "Music tuned to your taste"
    override val accountVerified = "Account verified"
    override val enterApp = "Start listening"
    override val continueWithGoogle = "Continue with Google"
    override val openingGoogle = "Opening Google accounts…"
    override val guestMode = "Use guest mode"
    override val privacyOtp =
        "Secure sign-in: verify ownership of your Google account with a 6-digit code sent to your email."
    override val privacyGoogle =
        "Sign in with a real Google account. Google verifies your identity and email — the app never stores your password."
    override val otpTitle = "Verify OTP"
    override val otpSendingTo = "Sending a code to:"
    override val otpSentTo = "A 6-digit code was sent to:"
    override val sendingEmail = "Sending email…"
    override val otpPlaceholder = "Enter the 6-digit code…"
    override val resendIn = "Resend in %s s"
    override val resend = "Resend code"
    override val confirmSignIn = "Confirm & sign in"
    override val checking = "Checking…"
    override val otpLengthError = "The code has 6 digits."
    override val otpNotConfigured = "Verification service is not configured."
    override val otpConfigWarning = "Couldn't send a code: %s"

    override val createPlaylistTitle = "New playlist"
    override val playlistName = "Playlist name"
    override val addToPlaylistTitle = "Add to playlist"
    override val newPlaylist = "New"
    override val newPlaylistName = "New playlist name"
    override val playlistNameExample = "e.g. Late-night chill…"
    override val createAndAdd = "Create & add"
    override val noPlaylists = "No playlists yet"
    override val createFirstPlaylistShort = "Create first playlist"
    override val deleteBrokenTitle = "Delete broken files"
    override val deleteBrokenBody =
        "You're about to delete %s broken files (%s MB). This frees up storage:"
    override val deleteForReal = "Delete"
    override val trackUnplayableFallback = "This file can't be played"

    override val addedToPlaylist = "Added '%s' to %s"
    override val createdPlaylistAndAdded = "Created playlist '%s' and added the track"
    override val deletedDownload = "Removed the download of '%s'"
    override val deleteDownloadFailed = "Couldn't remove the downloaded file '%s'"
    override val savedDownload = "Saved '%s' (%s MB) to Music/Onsu"
    override val localScanError =
        "Couldn't read your audio library. Check permission and scan again."
}

object VietnameseStrings : AppStrings {
    override val appName = "Onsu"

    override val navDiscover = "Khám phá"
    override val navLibrary = "Thư viện"
    override val navSearch = "Tìm kiếm"
    override val navSettings = "Cài đặt"
    override val back = "Quay lại"
    override val close = "Đóng"
    override val cancel = "Huỷ"
    override val create = "Tạo"
    override val retry = "Thử lại"
    override val retrying = "Đang thử lại…"
    override val searchAction = "Tìm kiếm"
    override val settingsAction = "Cài đặt"
    override val pause = "Tạm dừng"
    override val play = "Phát nhạc"
    override val nextTrack = "Bài tiếp theo"
    override val previousTrack = "Bài trước"
    override val like = "Thả tim"
    override val unlike = "Bỏ thích"
    override val addToPlaylistAction = "Thêm vào playlist"
    override val downloadAction = "Tải về để nghe ngoại tuyến"
    override val removeDownloadAction = "Đã tải, chạm để xoá"
    override val unplayable = "Không phát được"
    override val closePlayer = "Đóng màn hình phát"
    override val nowPlaying = "ĐANG PHÁT"
    override val onDeviceBadge = "MÁY"

    override val tasteFamiliar = "Quen thuộc"
    override val tasteBalanced = "Cân bằng"
    override val tasteExplore = "Khám phá"

    override val offlineTitle = "Bạn đang offline"
    override val offlineBody =
        "Không có kết nối mạng nên chưa tải được gợi ý mới. Nhạc đã tải về vẫn nghe được ở Thư viện → Đã tải."
    override val emptyQueueTitle = "Chưa có bài nào trong hàng đợi"
    override val emptyQueueBody =
        "Bấm tải lại để lấy gợi ý mới, hoặc chọn lại gu nhạc ở trên."
    override val reloadSuggestions = "Tải lại gợi ý"
    override val reloading = "Đang tải lại…"
    override val pagingMore = "Cuộn tiếp để tải thêm 10 bài"
    override val pagingEnd = "Đã hết gợi ý cho phiên này · kéo xuống để làm mới"

    override val stepLabel = "Bước %s"
    override val stepCounter = "1/2"
    override val seedsBubble =
        "Chọn 3 đến 5 thể loại bạn yêu thích nhất — tui dựa vào đó xếp danh sách nhé."
    override val favoriteGenres = "Thể loại yêu thích"
    override val genresHint = "Chọn ít nhất 3 — có thể đổi lại sau."
    override val selectedCount = "Đã chọn: %s/5 mục (tối thiểu 3)"
    override val continueLabel = "Tiếp tục"
    override val selectAtLeastThree = "Hãy chọn ít nhất 3 mục"
    override val folderBubble =
        "Nhạc bạn tải về máy nằm ở thư mục Music/ thì tui mới thấy và quét vào danh sách được."
    override val folderStandardLocation = "VỊ TRÍ THƯ MỤC CHUẨN TRÊN ANDROID"
    override val folderPutHere = "← để ở đây"
    override val folderNotVisible = "tui KHÔNG thấy"
    override val startListening = "Bắt đầu nghe"
    override val later = "Để sau"

    override val libraryTitle = "Thư viện"
    override val tabLiked = "Đã thích (%s)"
    override val tabPlaylists = "Playlist (%s)"
    override val tabDownloads = "Đã tải (%s)"
    override val emptyLikedTitle = "Chưa có bài nào được thả tim"
    override val emptyLikedBody = "Bấm biểu tượng ♥ ở bất kỳ bài hát nào để lưu vào đây."
    override val emptyPlaylistsTitle = "Chưa có danh sách phát nào"
    override val emptyPlaylistsBody =
        "Tạo playlist riêng để sắp xếp các bài hát bạn yêu thích!"
    override val createFirstPlaylist = "Tạo danh sách phát đầu tiên"
    override val privatePlaylist = "Riêng tư cá nhân"
    override val playlistDetailTitle = "Playlist cá nhân"
    override val playlistMeta = "%s bài · 100%% riêng tư"
    override val removeTrack = "Xoá"
    override val emptyDownloadsTitle = "Chưa tải bài nào về máy"
    override val emptyDownloadsBody =
        "Chạm biểu tượng tải xuống ở danh sách gợi ý để lưu bài vào thư mục Music/Onsu của máy."
    override val downloadsSummary = "%s bài · %s MB · phát được khi không có mạng"
    override val downloadsLocation = "Tệp nằm trong thư mục Music/Onsu của máy"

    override val searchPlaceholder = "Tìm bài hát, nghệ sĩ trong máy & online…"
    override val searchEmptyTitle = "Gõ gì đó đi"
    override val searchEmptyBody =
        "Tìm kiếm đồng thời bài hát có sẵn trong máy và kho nhạc mở trực tuyến."

    override val localTitle = "Nhạc trong máy"
    override val localNoPermission = "Chưa cấp quyền âm thanh"
    override val localScanning = "Đang quét âm thanh…"
    override val localFound = "%s tệp âm thanh tìm thấy"
    override val scanAgain = "Quét lại"
    override val localPermissionBody =
        "Onsu cần quyền đọc âm thanh để phát các tệp có sẵn trên máy."
    override val grantAudioPermission = "Cấp quyền âm thanh"
    override val openAppSettings = "Mở cài đặt ứng dụng"
    override val localEmptyTitle = "Chưa tìm thấy âm thanh trên máy"
    override val localEmptyBody =
        "Quét lại sau khi tải tệp vào Music/, Download/ hoặc thư mục ghi âm. Thư mục riêng của ứng dụng khác có thể không truy cập được."

    override val settingsTitle = "Cài đặt"
    override val guestName = "Khách"
    override val tapToSignIn = "Chạm để đăng nhập Google"
    override val signIn = "Đăng nhập"
    override val change = "Đổi"
    override val manageLocalMusic = "Quản lý nhạc và âm thanh trong máy"
    override val preferencesLabel = "TUỲ CHỈNH"
    override val languageLabel = "Ngôn ngữ"
    override val languageEnglish = "English"
    override val languageVietnamese = "Tiếng Việt"
    override val themeLabel = "Giao diện"
    override val themeDark = "Tối"
    override val themeLight = "Sáng"
    override val themeSystem = "Hệ thống"
    override val tasteSectionTitle = "GU ÂM NHẠC"
    override val tasteSectionBody =
        "Đổi phong cách nghe nhạc bằng cách chọn lại 3–5 thể loại yêu thích."
    override val redoOnboarding = "Làm lại Onboarding"
    override val signOutGoogle = "Đăng xuất tài khoản Google"

    override val profileTitle = "Hồ sơ"
    override val accountLabel = "TÀI KHOẢN"
    override val accountGoogle = "Đã đăng nhập bằng Google"
    override val accountGuest = "Chế độ khách — thư viện lưu trên máy này"
    override val switchAccount = "Đổi tài khoản"
    override val signOutAction = "Đăng xuất"
    override val notSignedIn = "Chưa đăng nhập"

    override val authTagline = "Âm nhạc cá nhân hoá theo gu của bạn"
    override val accountVerified = "Tài khoản đã xác thực"
    override val enterApp = "Vào nghe nhạc ngay"
    override val continueWithGoogle = "Tiếp tục với Google"
    override val openingGoogle = "Đang mở tài khoản Google…"
    override val guestMode = "Dùng chế độ Khách"
    override val privacyOtp =
        "Xác thực an toàn: Kiểm tra quyền sở hữu tài khoản Google thông qua mã xác nhận 6 số gửi tới email của bạn."
    override val privacyGoogle =
        "Đăng nhập bằng tài khoản Google thật. Danh tính và email do Google xác thực — app không lưu mật khẩu của bạn."
    override val otpTitle = "Xác nhận mã OTP"
    override val otpSendingTo = "Đang gửi mã tới địa chỉ:"
    override val otpSentTo = "Mã xác thực 6 số đã được gửi tới:"
    override val sendingEmail = "Đang gửi email…"
    override val otpPlaceholder = "Nhập 6 số mã xác nhận…"
    override val resendIn = "Gửi lại sau %s giây"
    override val resend = "Gửi lại mã mới"
    override val confirmSignIn = "Xác nhận & Đăng nhập"
    override val checking = "Đang kiểm tra…"
    override val otpLengthError = "Mã xác nhận gồm 6 chữ số."
    override val otpNotConfigured = "Chưa cấu hình dịch vụ xác thực mã."
    override val otpConfigWarning = "Chưa gửi được mã xác nhận: %s"

    override val createPlaylistTitle = "Tạo Playlist mới"
    override val playlistName = "Tên playlist"
    override val addToPlaylistTitle = "Thêm vào playlist"
    override val newPlaylist = "Tạo mới"
    override val newPlaylistName = "Tên playlist mới"
    override val playlistNameExample = "Ví dụ: Nhạc chill đêm khuya…"
    override val createAndAdd = "Tạo & Thêm"
    override val noPlaylists = "Chưa có danh sách phát nào"
    override val createFirstPlaylistShort = "Tạo playlist đầu tiên"
    override val deleteBrokenTitle = "Xác nhận xoá tệp lỗi"
    override val deleteBrokenBody =
        "Bạn sắp xoá %s tệp lỗi (%s MB). Thao tác này sẽ giải phóng dung lượng bộ nhớ:"
    override val deleteForReal = "Xoá thật"
    override val trackUnplayableFallback = "Tệp không phát được"

    override val addedToPlaylist = "Đã thêm '%s' vào %s"
    override val createdPlaylistAndAdded = "Đã tạo playlist '%s' và thêm bài hát"
    override val deletedDownload = "Đã xoá bản tải về của '%s'"
    override val deleteDownloadFailed = "Không xoá được tệp đã tải của '%s'"
    override val savedDownload = "Đã lưu '%s' (%s MB) vào thư mục Music/Onsu"
    override val localScanError =
        "Không đọc được thư viện âm thanh. Hãy kiểm tra quyền và quét lại."
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

fun appStrings(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.VIETNAMESE -> VietnameseStrings
}

fun AppStrings.tasteLabel(level: TasteLevel): String = when (level) {
    TasteLevel.FAMILIAR -> tasteFamiliar
    TasteLevel.BALANCED -> tasteBalanced
    TasteLevel.EXPLORE -> tasteExplore
}
