package com.onsu

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.onsu.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kiểm tra migration v1 → v2 trên một database v1 thật.
 *
 * Room validate schema sau khi migrate, nên test này bắt được cả lỗi SQL lẫn lỗi lệch
 * kiểu/khoá chính — những lỗi mà chỉ xuất hiện trên máy đã cài bản cũ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    @Test
    fun `migration moves legacy library to the signed-in account`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dbName = "migration_test.db"
        context.deleteDatabase(dbName)
        context.getDatabasePath(dbName).parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        legacy.createVersionOneSchema()
        legacy.execSQL(
            "INSERT INTO users (id, email, displayName, photoUrl, isLoggedIn) " +
                "VALUES ('acc_legacy', 'legacy@example.com', 'Legacy', NULL, 1)"
        )
        legacy.execSQL(
            "INSERT INTO liked_tracks (trackId, title, artist, album, artworkUrl, streamUrl, " +
                "source, sourceId, durationMs, genre, likedAt) " +
                "VALUES ('track_old', 'Old song', 'Old artist', 'Old album', NULL, " +
                "'https://example.invalid/old.mp3', 'AUDIUS', 'old', 180000, 'Pop', 1)"
        )
        legacy.execSQL(
            "INSERT INTO playlists (id, title, isPrivate, createdAt) VALUES ('pl_old', 'Old playlist', 1, 1)"
        )
        legacy.execSQL(
            "INSERT INTO playlist_items (playlistId, position, trackId, title, artist, album, " +
                "artworkUrl, streamUrl, source, sourceId, durationMs, genre) " +
                "VALUES ('pl_old', 0, 'track_old', 'Old song', 'Old artist', 'Old album', NULL, " +
                "'https://example.invalid/old.mp3', 'AUDIUS', 'old', 180000, 'Pop')"
        )
        legacy.execSQL(
            "INSERT INTO taste_profile (id, selectedArtists, selectedGenres, updatedAt) " +
                "VALUES (1, 'Vũ.', 'Indie', 1)"
        )
        legacy.version = 1
        legacy.close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        try {
            val liked = room.likedTrackDao().getAllLikedTracks("acc_legacy").first()
            assertEquals(1, liked.size)
            assertEquals("track_old", liked.first().trackId)

            val playlists = room.playlistDao().getAllPlaylists("acc_legacy").first()
            assertEquals(1, playlists.size)
            assertEquals("pl_old", playlists.first().id)

            val items = room.playlistDao().getItemsForPlaylist("pl_old", "acc_legacy").first()
            assertEquals(1, items.size)

            val seeds = room.tasteProfileDao().getTasteProfile("acc_legacy")
            assertEquals("Vũ.", seeds?.selectedArtists)

            // Không tài khoản nào khác nhìn thấy dữ liệu cũ.
            assertTrue(room.likedTrackDao().getAllLikedTracks("someone_else").first().isEmpty())

            // v2 → v3 chỉ thêm bảng tải nhạc; bản nâng cấp không được mang theo bản ghi nào.
            // v3 → v4 thêm cột `contentUri`/`displayName` cho tệp trong thư mục Music.
            assertTrue(room.downloadDao().getAllDownloadsOnce().isEmpty())
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    /** Schema v1 đúng như bản phát hành trước khi thêm cột `ownerId`. */
    private fun SQLiteDatabase.createVersionOneSchema() {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `playback_session` (`id` INTEGER NOT NULL, " +
                "`trackId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                "`album` TEXT NOT NULL, `artworkUrl` TEXT, `streamUrl` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`genre` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `playlistMode` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS `liked_tracks` (`trackId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, " +
                "`artworkUrl` TEXT, `streamUrl` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`sourceId` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `genre` TEXT NOT NULL, " +
                "`likedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`isPrivate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_items` (`playlistId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT, " +
                "`streamUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `genre` TEXT NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `position`))"
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS `taste_profile` (`id` INTEGER NOT NULL, " +
                "`selectedArtists` TEXT NOT NULL, `selectedGenres` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        execSQL(
            "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `photoUrl` TEXT, `isLoggedIn` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
