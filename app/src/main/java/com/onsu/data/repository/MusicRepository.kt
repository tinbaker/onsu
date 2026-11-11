package com.onsu.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.onsu.data.download.DownloadOutcome
import com.onsu.data.download.DownloadPublisher
import com.onsu.data.download.DownloadStore
import com.onsu.data.download.MediaStorePublisher
import com.onsu.data.download.PublishRequest
import com.onsu.data.download.TrackDownloader
import com.onsu.data.local.AppDatabase
import com.onsu.data.local.entities.DownloadedTrackEntity
import com.onsu.data.local.entities.GUEST_OWNER_ID
import com.onsu.data.local.entities.LikedTrackEntity
import com.onsu.data.local.entities.PlaybackSessionEntity
import com.onsu.data.local.entities.PlaylistEntity
import com.onsu.data.local.entities.PlaylistItemEntity
import com.onsu.data.local.entities.TasteProfileEntity
import com.onsu.data.local.entities.UserEntity
import com.onsu.data.models.PlaybackSession
import com.onsu.data.models.Playlist
import com.onsu.data.models.TasteLevel
import com.onsu.data.models.Track
import com.onsu.data.models.TrackSource
import com.onsu.data.models.UserProfile
import com.onsu.data.remote.RemoteMusicCatalog
import com.onsu.data.scanner.LocalMusicScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Nguồn dữ liệu duy nhất của app.
 *
 * Thư viện (bài thích, playlist, gu nhạc, phiên nghe dở) được khoá theo `ownerId`.
 * Repository tự xác định chủ sở hữu hiện tại — Activity không phải truyền vào — nên
 * đổi tài khoản là mọi luồng dữ liệu tự chuyển sang thư viện của người mới.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepository(
    private val context: Context,
    private val fetchRemoteCatalog: suspend () -> List<Track> = { RemoteMusicCatalog.fetchAudiusCatalog() },
    private val publisher: DownloadPublisher = MediaStorePublisher(context),
    private val downloader: TrackDownloader = TrackDownloader(context, publisher = publisher)
) {
    private val db = AppDatabase.getInstance(context)

    // Separate locks: a slow network fetch must never block a device scan.
    private val remoteMutex = Mutex()
    private val localMutex = Mutex()

    private var cachedRemotePool: List<Track>? = null
    private var cachedLocalTracks: List<Track>? = null
    private var cachedLocalPermission: Boolean? = null

    /** Id tài khoản đang đăng nhập; chưa đăng nhập thì là thư viện khách. */
    private suspend fun currentOwnerId(): String =
        db.userDao().getCurrentUser()?.id ?: GUEST_OWNER_ID

    private fun currentOwnerFlow(): Flow<String> =
        db.userDao().getCurrentUserFlow()
            .map { it?.id ?: GUEST_OWNER_ID }
            .distinctUntilChanged()

    val likedTracksFlow: Flow<List<Track>> = currentOwnerFlow()
        .flatMapLatest { owner -> db.likedTrackDao().getAllLikedTracks(owner) }
        .map { list ->
            list.map { entity ->
                Track(
                    id = entity.trackId,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    artworkUrl = entity.artworkUrl,
                    durationMs = entity.durationMs,
                    genre = entity.genre,
                    source = TrackSource.valueOf(entity.source),
                    sourceId = entity.sourceId,
                    audioUrl = entity.streamUrl,
                    isLiked = true
                )
            }
        }

    val playlistsFlow: Flow<List<Playlist>> = currentOwnerFlow()
        .flatMapLatest { owner -> db.playlistDao().getAllPlaylists(owner) }
        .map { list ->
            list.map { entity ->
                Playlist(
                    id = entity.id,
                    title = entity.title,
                    isPrivate = entity.isPrivate,
                    createdAt = entity.createdAt
                )
            }
        }

    val userProfileFlow: Flow<UserProfile?> = db.userDao().getCurrentUserFlow().map { entity ->
        entity?.let {
            UserProfile(
                id = it.id,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoUrl,
                isLoggedIn = it.isLoggedIn,
                isGuest = false
            )
        }
    }

    suspend fun saveLikedTrack(track: Track) {
        db.likedTrackDao().insertLikedTrack(
            LikedTrackEntity(
                ownerId = currentOwnerId(),
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                streamUrl = track.audioUrl,
                source = track.source.name,
                sourceId = track.sourceId,
                durationMs = track.durationMs,
                genre = track.genre
            )
        )
    }

    suspend fun removeLikedTrack(trackId: String) {
        db.likedTrackDao().deleteLikedTrack(currentOwnerId(), trackId)
    }

    suspend fun isTrackLiked(trackId: String): Boolean {
        return db.likedTrackDao().isLiked(currentOwnerId(), trackId)
    }

    suspend fun createPlaylist(title: String): String {
        val id = java.util.UUID.randomUUID().toString()
        db.playlistDao().insertPlaylist(
            PlaylistEntity(
                id = id,
                ownerId = currentOwnerId(),
                title = title,
                isPrivate = true
            )
        )
        return id
    }

    suspend fun deletePlaylist(playlistId: String) {
        db.playlistDao().deletePlaylist(currentOwnerId(), playlistId)
    }

    fun getTracksForPlaylist(playlistId: String): Flow<List<Track>> {
        return currentOwnerFlow()
            .flatMapLatest { owner -> db.playlistDao().getItemsForPlaylist(playlistId, owner) }
            .map { items ->
                items.map { entity ->
                    Track(
                        id = entity.trackId,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        artworkUrl = entity.artworkUrl,
                        durationMs = entity.durationMs,
                        genre = entity.genre,
                        source = TrackSource.valueOf(entity.source),
                        sourceId = entity.sourceId,
                        audioUrl = entity.streamUrl
                    )
                }
            }
    }

    /**
     * Thêm track vào playlist.
     *
     * `position` để null nghĩa là "nối vào cuối" — repository tự lấy vị trí kế tiếp.
     * Bắt buộc phải tự tính vì `playlist_items` có khóa chính tổ hợp (playlistId, position)
     * và insert dùng REPLACE: truyền cứng position = 0 sẽ ghi đè bài trước đó.
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: Track, position: Int? = null) {
        val dao = db.playlistDao()
        if (!dao.isOwnedBy(playlistId, currentOwnerId())) return
        if (dao.containsTrack(playlistId, track.id)) return
        val targetPosition = position ?: dao.getNextPosition(playlistId)

        dao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                position = targetPosition,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                streamUrl = track.audioUrl,
                source = track.source.name,
                sourceId = track.sourceId,
                durationMs = track.durationMs,
                genre = track.genre
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val dao = db.playlistDao()
        if (!dao.isOwnedBy(playlistId, currentOwnerId())) return
        dao.removeItemFromPlaylist(playlistId, trackId)
    }

    // ---- Tải nhạc online về máy ----

    /**
     * Danh sách bài đã tải, dùng cho tab "Đã tải" trong Thư viện.
     *
     * `onStart` nạp lại bảng tra cứu tệp trong RAM trước lần phát đầu tiên, nên chỉ
     * cần mở thư viện là bài tải sẵn sàng phát ngoại tuyến — kể cả khi Activity được
     * tạo lại sau khi tiến trình bị hệ thống thu hồi.
     */
    val downloadedTracksFlow: Flow<List<Track>> = db.downloadDao().getAllDownloads()
        .onStart { refreshDownloadRegistry() }
        .map { rows -> rows.map { it.toTrack() } }

    /**
     * Đối chiếu bảng `downloaded_tracks` với bộ nhớ máy: tệp còn thì đăng ký để phát
     * ngoại tuyến, tệp mất (người dùng xoá bằng ứng dụng khác, dọn bộ nhớ) thì bỏ luôn
     * bản ghi để danh sách không hiện bài "đã tải" mà bấm vào lại phát từ mạng.
     *
     * Bản ghi tạo từ v3 chưa có `contentUri` (tệp còn nằm trong bộ nhớ riêng của app):
     * được chuyển sang `Music/Onsu` ngay tại đây, nên bài đã tải trước khi cập nhật
     * cũng xuất hiện trong thư mục nhạc của máy. Nếu chưa chuyển được (thiếu quyền ghi
     * trên Android 9 trở xuống) thì vẫn phát bằng tệp cũ và thử lại lần mở app sau.
     */
    suspend fun refreshDownloadRegistry() = withContext(Dispatchers.IO) {
        val rows = db.downloadDao().getAllDownloadsOnce()
        DownloadStore.clear()
        val missing = mutableListOf<String>()

        for (row in rows) {
            val stored = row.contentUri
            if (stored.isNotBlank()) {
                val uri = Uri.parse(stored)
                if (publisher.exists(uri)) DownloadStore.register(row.trackId, stored)
                else missing.add(row.trackId)
                continue
            }

            val legacy = DownloadStore.legacyFile(context, row.fileName)
            if (legacy == null || !legacy.exists() || legacy.length() <= 0L) {
                missing.add(row.trackId)
                continue
            }
            val migrated = runCatching { migrateLegacyDownload(row, legacy) }.getOrNull()
            DownloadStore.register(
                row.trackId,
                migrated ?: Uri.fromFile(legacy).toString()
            )
        }

        for (trackId in missing) db.downloadDao().deleteDownload(trackId)
    }

    /** Đưa tệp của bản ghi v3 sang thư mục nhạc dùng chung; trả về `Uri` mới. */
    private suspend fun migrateLegacyDownload(
        row: DownloadedTrackEntity,
        legacy: java.io.File
    ): String {
        val extension = legacy.name.substringAfterLast('.', "mp3").lowercase().ifBlank { "mp3" }
        val published = publisher.publish(
            legacy,
            PublishRequest(
                displayName = DownloadStore.fileName(row.artist, row.title, extension),
                mimeType = TrackDownloader.mimeTypeFor(extension),
                title = row.title,
                artist = row.artist,
                album = row.album
            )
        )
        db.downloadDao().saveDownload(
            row.copy(
                contentUri = published.uri.toString(),
                displayName = published.displayName,
                fileName = published.displayName,
                fileSizeBytes = published.fileSizeBytes
            )
        )
        legacy.delete()
        return published.uri.toString()
    }

    /**
     * Tải một bài online về máy.
     *
     * Chỉ ghi vào Room sau khi tệp đã nằm trong thư mục nhạc của máy, nên danh sách
     * "Đã tải" không bao giờ chứa một bản ghi trỏ tới tệp dở dang.
     */
    suspend fun downloadTrack(
        track: Track,
        onProgress: (Float) -> Unit = {}
    ): DownloadOutcome {
        val outcome = downloader.download(track, onProgress)
        if (outcome is DownloadOutcome.Success) {
            db.downloadDao().saveDownload(
                DownloadedTrackEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl,
                    streamUrl = track.audioUrl,
                    source = track.source.name,
                    sourceId = track.sourceId,
                    durationMs = track.durationMs,
                    genre = track.genre,
                    fileName = outcome.displayName,
                    fileSizeBytes = outcome.fileSizeBytes,
                    contentUri = outcome.uri.toString(),
                    displayName = outcome.displayName
                )
            )
        }
        return outcome
    }

    /** Xoá bản tải về (tệp trong thư mục nhạc + bản ghi). False khi không có gì để xoá. */
    suspend fun deleteDownload(trackId: String): Boolean {
        val row = db.downloadDao().getDownload(trackId) ?: return false
        val target = row.contentUri.ifBlank {
            DownloadStore.legacyFile(context, row.fileName)?.let { Uri.fromFile(it).toString() }.orEmpty()
        }
        val deleted = target.isBlank() || publisher.delete(Uri.parse(target))
        DownloadStore.unregister(trackId)
        db.downloadDao().deleteDownload(trackId)
        return deleted
    }

    suspend fun downloadedTrackIds(): Set<String> =
        db.downloadDao().getAllDownloadsOnce().mapTo(HashSet()) { it.trackId }

    /**
     * Bản ghi trong Room không giữ đường dẫn tuyệt đối (thư mục dữ liệu app đổi theo
     * máy và theo lần cài), nên `audioUrl` vẫn là URL stream. Việc phát ngoại tuyến do
     * [DownloadStore] quyết định ở tầng player.
     */
    private fun DownloadedTrackEntity.toTrack(): Track = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        genre = genre,
        source = TrackSource.valueOf(source),
        sourceId = sourceId,
        audioUrl = streamUrl,
        isDownloaded = true,
        fileSizeBytes = fileSizeBytes
    )

    // Playback session save & restore
    suspend fun savePlaybackSession(session: PlaybackSession) {
        db.sessionDao().saveSession(
            PlaybackSessionEntity(
                ownerId = currentOwnerId(),
                trackId = session.trackId,
                title = session.title,
                artist = session.artist,
                album = session.album,
                artworkUrl = session.artworkUrl,
                streamUrl = session.streamUrl,
                source = session.source.name,
                sourceId = session.sourceId,
                durationMs = session.durationMs,
                genre = session.genre,
                positionMs = session.positionMs,
                playlistMode = session.playlistMode.name
            )
        )
    }

    suspend fun restorePlaybackSession(): PlaybackSession? {
        val entity = db.sessionDao().getSession(currentOwnerId()) ?: return null
        val source = TrackSource.valueOf(entity.source)
        val resolvedUrl = RemoteMusicCatalog.resolveStreamUrl(source, entity.sourceId, entity.streamUrl)

        return PlaybackSession(
            trackId = entity.trackId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            artworkUrl = entity.artworkUrl,
            streamUrl = resolvedUrl,
            source = source,
            sourceId = entity.sourceId,
            durationMs = entity.durationMs,
            genre = entity.genre,
            positionMs = entity.positionMs,
            playlistMode = TasteLevel.valueOf(entity.playlistMode)
        )
    }

    // Taste vector persistence
    suspend fun saveTasteSeeds(artists: Set<String>, genres: Set<String>) {
        db.tasteProfileDao().saveTasteProfile(
            TasteProfileEntity(
                ownerId = currentOwnerId(),
                selectedArtists = artists.joinToString(","),
                selectedGenres = genres.joinToString(",")
            )
        )
    }

    suspend fun getTasteSeeds(): Pair<Set<String>, Set<String>> {
        val profile = db.tasteProfileDao().getTasteProfile(currentOwnerId())
            ?: return Pair(emptySet(), emptySet())
        val artists = profile.selectedArtists.split(",").filter { it.isNotBlank() }.toSet()
        val genres = profile.selectedGenres.split(",").filter { it.isNotBlank() }.toSet()
        return Pair(artists, genres)
    }

    /**
     * Lưu hồ sơ người dùng đang đăng nhập.
     *
     * Bảng `users` được đọc bằng `SELECT * FROM users LIMIT 1`, nên nó phải luôn chỉ có
     * **tối đa một dòng** — đây là "tài khoản đang dùng trên máy", không phải danh bạ.
     * Xoá và ghi nằm trong cùng một transaction để observer không thấy trạng thái trung
     * gian (nếu không, UI sẽ loé lên thư viện khách giữa lúc đổi tài khoản).
     *
     * Thư viện của từng người không bị xoá: chúng nằm ở các bảng khác, khoá theo ownerId.
     */
    suspend fun saveUserProfile(user: UserProfile) {
        db.withTransaction {
            db.userDao().clearUser()
            db.userDao().saveUser(
                UserEntity(
                    id = user.id,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl,
                    isLoggedIn = user.isLoggedIn
                )
            )
        }
    }

    suspend fun clearUser() {
        db.userDao().clearUser()
    }

    /**
     * The online catalog used for recommendations.
     *
     * Deliberately **excludes device audio**: the automatic playlist is an online
     * discovery feature, and songs stored on the phone must never be suggested there.
     * The remote result is cached separately so a reload does not touch local files.
     */
    suspend fun loadRecommendationPool(forceRefresh: Boolean = false): List<Track> = remoteMutex.withLock {
        val cached = cachedRemotePool
        if (cached != null && !forceRefresh) return@withLock cached

        val fetched = try {
            buildRemotePool()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyList()
        }
        if (fetched.isNotEmpty()) {
            cachedRemotePool = fetched
            fetched
        } else {
            cached ?: emptyList()
        }
    }

    /**
     * Device audio for the local library screen only.
     *
     * The cache is keyed on the audio permission so granting or revoking access always
     * produces a fresh scan, while repeated visits to the screen reuse the last result.
     */
    suspend fun loadLocalTracks(forceRefresh: Boolean = false): List<Track> = localMutex.withLock {
        val granted = LocalMusicScanner.hasAudioPermission(context)
        val cached = cachedLocalTracks
        if (cached != null && !forceRefresh && cachedLocalPermission == granted) {
            return@withLock cached
        }

        val scanned = if (granted) LocalMusicScanner.scanMusic(context) else emptyList()
        // A permission change during the scan would publish a stale list.
        val permissionAtPublish = LocalMusicScanner.hasAudioPermission(context)
        val result = if (permissionAtPublish) scanned else emptyList()

        cachedLocalTracks = result
        cachedLocalPermission = if (permissionAtPublish == granted) granted else null
        result
    }

    /**
     * Combined pool (device + online) used by search and by queue restoration, where a
     * local file the user is already playing still has to be resolvable by id.
     */
    suspend fun loadCandidatePool(forceRefresh: Boolean = false): List<Track> {
        val local = loadLocalTracks(forceRefresh)
        val remote = loadRecommendationPool(forceRefresh = false)
        return mergeCandidatePool(local, remote)
    }

    private suspend fun buildRemotePool(): List<Track> {
        val pool = fetchRemoteCatalog().toMutableList()
        // Không có bài nào từ mạng nghĩa là đang offline (hoặc Audius chết hẳn): trả
        // rỗng để UI hiện thông báo offline thay vì lấp bằng kho nhạc Creative Commons
        // — chính là những "đề xuất bừa bãi" người dùng thấy khi mất mạng.
        if (pool.isEmpty()) return emptyList()

        if (pool.size < 80) {
            val baseCc = RemoteMusicCatalog.getCuratedCreativeCommonsPool()
            var index = 0
            while (pool.size < 85 && baseCc.isNotEmpty()) {
                val template = baseCc[index % baseCc.size]
                val uniqueId = if (index < baseCc.size) template.id else "${template.id}_sub_$index"
                pool.add(template.copy(id = uniqueId))
                index++
            }
        }
        return pool
    }

    companion object {
        /** The 100-track discovery cap applies only to remote music, never device files. */
        internal fun mergeCandidatePool(local: List<Track>, remote: List<Track>): List<Track> =
            (local + remote.filter { it.audioUrl.isNotBlank() }.distinctBy { it.id }.take(100))
                .filter { it.audioUrl.isNotBlank() }
                .distinctBy { it.id }
    }
}
