package com.onsu.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onsu.data.local.dao.DownloadDao
import com.onsu.data.local.dao.LikedTrackDao
import com.onsu.data.local.dao.PlaylistDao
import com.onsu.data.local.dao.SessionDao
import com.onsu.data.local.dao.TasteProfileDao
import com.onsu.data.local.dao.UserDao
import com.onsu.data.local.entities.DownloadedTrackEntity
import com.onsu.data.local.entities.GUEST_OWNER_ID
import com.onsu.data.local.entities.LikedTrackEntity
import com.onsu.data.local.entities.PlaybackSessionEntity
import com.onsu.data.local.entities.PlaylistEntity
import com.onsu.data.local.entities.PlaylistItemEntity
import com.onsu.data.local.entities.TasteProfileEntity
import com.onsu.data.local.entities.UserEntity

@Database(
    entities = [
        PlaybackSessionEntity::class,
        LikedTrackEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        TasteProfileEntity::class,
        UserEntity::class,
        DownloadedTrackEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun likedTrackDao(): LikedTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun tasteProfileDao(): TasteProfileDao
    abstract fun userDao(): UserDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2: thêm chủ sở hữu cho thư viện.
         *
         * Bản cũ không có cột `ownerId`, nên toàn bộ like/playlist/gu nhạc/phiên nghe bị
         * dùng chung cho mọi tài khoản. Dữ liệu đang có được gán cho tài khoản đang đăng
         * nhập (hoặc `guest` nếu chưa đăng nhập) để không mất thư viện của người dùng
         * hiện tại khi cập nhật app.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val legacyOwner = db.query("SELECT id FROM users LIMIT 1").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else GUEST_OWNER_ID
                }
                val owner = legacyOwner.replace("'", "''")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playback_session_new` (" +
                        "`ownerId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT, " +
                        "`streamUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `genre` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                        "`playlistMode` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`))"
                )
                db.execSQL(
                    "INSERT INTO `playback_session_new` (`ownerId`, `trackId`, `title`, `artist`, " +
                        "`album`, `artworkUrl`, `streamUrl`, `source`, `sourceId`, `durationMs`, " +
                        "`genre`, `positionMs`, `playlistMode`, `updatedAt`) " +
                        "SELECT '$owner', `trackId`, `title`, `artist`, `album`, `artworkUrl`, " +
                        "`streamUrl`, `source`, `sourceId`, `durationMs`, `genre`, `positionMs`, " +
                        "`playlistMode`, `updatedAt` FROM `playback_session`"
                )
                db.execSQL("DROP TABLE `playback_session`")
                db.execSQL("ALTER TABLE `playback_session_new` RENAME TO `playback_session`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `liked_tracks_new` (" +
                        "`ownerId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT, " +
                        "`streamUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `genre` TEXT NOT NULL, `likedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `trackId`))"
                )
                db.execSQL(
                    "INSERT INTO `liked_tracks_new` (`ownerId`, `trackId`, `title`, `artist`, " +
                        "`album`, `artworkUrl`, `streamUrl`, `source`, `sourceId`, `durationMs`, " +
                        "`genre`, `likedAt`) " +
                        "SELECT '$owner', `trackId`, `title`, `artist`, `album`, `artworkUrl`, " +
                        "`streamUrl`, `source`, `sourceId`, `durationMs`, `genre`, `likedAt` " +
                        "FROM `liked_tracks`"
                )
                db.execSQL("DROP TABLE `liked_tracks`")
                db.execSQL("ALTER TABLE `liked_tracks_new` RENAME TO `liked_tracks`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists_new` (" +
                        "`id` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`isPrivate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `playlists_new` (`id`, `ownerId`, `title`, `isPrivate`, `createdAt`) " +
                        "SELECT `id`, '$owner', `title`, `isPrivate`, `createdAt` FROM `playlists`"
                )
                db.execSQL("DROP TABLE `playlists`")
                db.execSQL("ALTER TABLE `playlists_new` RENAME TO `playlists`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `taste_profile_new` (" +
                        "`ownerId` TEXT NOT NULL, `selectedArtists` TEXT NOT NULL, " +
                        "`selectedGenres` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`))"
                )
                db.execSQL(
                    "INSERT INTO `taste_profile_new` (`ownerId`, `selectedArtists`, `selectedGenres`, `updatedAt`) " +
                        "SELECT '$owner', `selectedArtists`, `selectedGenres`, `updatedAt` FROM `taste_profile`"
                )
                db.execSQL("DROP TABLE `taste_profile`")
                db.execSQL("ALTER TABLE `taste_profile_new` RENAME TO `taste_profile`")
            }
        }

        /**
         * v2 → v3: thêm bảng bài đã tải về máy.
         *
         * Chỉ tạo bảng mới, không đụng dữ liệu cũ — thư viện, gu nhạc và phiên nghe
         * của mọi tài khoản giữ nguyên.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloaded_tracks` (" +
                        "`trackId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, `artworkUrl` TEXT, `streamUrl` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`genre` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`fileSizeBytes` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trackId`))"
                )
            }
        }

        /**
         * v3 → v4: lưu `Uri` của tệp trong thư mục nhạc dùng chung.
         *
         * v3 ghi tệp vào bộ nhớ riêng của app nên bài đã tải không thấy được bằng ứng
         * dụng quản lý tệp hay app nhạc khác. Cột `contentUri` cho biết tệp nằm ở đâu;
         * bản ghi cũ để trống và được chuyển sang `Music/Onsu` khi khởi động.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `downloaded_tracks` ADD COLUMN `contentUri` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `downloaded_tracks` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mixtape_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
