package com.onsu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onsu.data.local.entities.DownloadedTrackEntity
import com.onsu.data.local.entities.LikedTrackEntity
import com.onsu.data.local.entities.PlaybackSessionEntity
import com.onsu.data.local.entities.PlaylistEntity
import com.onsu.data.local.entities.PlaylistItemEntity
import com.onsu.data.local.entities.TasteProfileEntity
import com.onsu.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM playback_session WHERE ownerId = :ownerId LIMIT 1")
    suspend fun getSession(ownerId: String): PlaybackSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: PlaybackSessionEntity)
}

@Dao
interface LikedTrackDao {
    @Query("SELECT * FROM liked_tracks WHERE ownerId = :ownerId ORDER BY likedAt DESC")
    fun getAllLikedTracks(ownerId: String): Flow<List<LikedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTrack(track: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE ownerId = :ownerId AND trackId = :trackId")
    suspend fun deleteLikedTrack(ownerId: String, trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE ownerId = :ownerId AND trackId = :trackId)")
    suspend fun isLiked(ownerId: String, trackId: String): Boolean
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getAllPlaylists(ownerId: String): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND ownerId = :ownerId")
    suspend fun deletePlaylist(ownerId: String, playlistId: String)

    /**
     * Join sang `playlists` để id playlist của tài khoản khác (dù đoán được) cũng không
     * đọc ra được bài hát.
     */
    @Query(
        "SELECT items.* FROM playlist_items AS items " +
            "INNER JOIN playlists AS p ON p.id = items.playlistId " +
            "WHERE items.playlistId = :playlistId AND p.ownerId = :ownerId " +
            "ORDER BY items.position ASC"
    )
    fun getItemsForPlaylist(playlistId: String, ownerId: String): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeItemFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun containsTrack(playlistId: String, trackId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE id = :playlistId AND ownerId = :ownerId)")
    suspend fun isOwnedBy(playlistId: String, ownerId: String): Boolean
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadsOnce(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownload(trackId: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE trackId = :trackId")
    suspend fun deleteDownload(trackId: String)
}

@Dao
interface TasteProfileDao {
    @Query("SELECT * FROM taste_profile WHERE ownerId = :ownerId LIMIT 1")
    suspend fun getTasteProfile(ownerId: String): TasteProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTasteProfile(profile: TasteProfileEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUser()
}
