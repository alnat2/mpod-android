package com.example.mpod.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MpodDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `pending_share_imports` (
                    `token` TEXT NOT NULL,
                    `payload` TEXT NOT NULL,
                    `title` TEXT,
                    `status` TEXT NOT NULL,
                    `sourceUri` TEXT,
                    `copyPath` TEXT,
                    `error` TEXT,
                    `attempts` INTEGER NOT NULL,
                    `batchId` TEXT NOT NULL,
                    `fingerprint` TEXT NOT NULL,
                    `progressBytes` INTEGER NOT NULL,
                    `totalBytes` INTEGER,
                    `completionAcknowledged` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`token`)
                )""".trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_share_imports_status_createdAt` " +
                    "ON `pending_share_imports` (`status`, `createdAt`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_share_imports_batchId` " +
                    "ON `pending_share_imports` (`batchId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_share_imports_fingerprint_createdAt` " +
                    "ON `pending_share_imports` (`fingerprint`, `createdAt`)"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `audiobooks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `folderUri` TEXT NOT NULL,
                    `coverUri` TEXT,
                    `durationSeconds` INTEGER NOT NULL,
                    `isSingleFile` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL
                )""".trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobooks_folderUri` ON `audiobooks` (`folderUri`)"
            )

            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `audiobook_tracks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `audiobookId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `fileUri` TEXT NOT NULL,
                    `trackNumber` INTEGER NOT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    `playbackPositionMs` INTEGER NOT NULL,
                    `isListened` INTEGER NOT NULL,
                    `isAvailable` INTEGER NOT NULL,
                    FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_audiobook_tracks_audiobookId` ON `audiobook_tracks` (`audiobookId`)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobook_tracks_audiobookId_fileUri` " +
                    "ON `audiobook_tracks` (`audiobookId`, `fileUri`)"
            )

            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `audiobook_playlist_tracks` (
                    `audiobookId` INTEGER NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`audiobookId`, `trackId`),
                    FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`trackId`) REFERENCES `audiobook_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_audiobook_playlist_tracks_audiobookId` ON `audiobook_playlist_tracks` (`audiobookId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_audiobook_playlist_tracks_trackId` ON `audiobook_playlist_tracks` (`trackId`)"
            )

            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `playlist_items_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `episodeId` INTEGER,
                    `audiobookId` INTEGER,
                    `position` INTEGER NOT NULL,
                    FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    CHECK ((`episodeId` IS NOT NULL AND `audiobookId` IS NULL) OR (`episodeId` IS NULL AND `audiobookId` IS NOT NULL))
                )""".trimIndent()
            )
            database.execSQL(
                """INSERT INTO `playlist_items_new` (`id`, `episodeId`, `audiobookId`, `position`)
                   SELECT `id`, `episodeId`, NULL, `position` FROM `playlist_items`""".trimIndent()
            )
            database.execSQL("DROP TABLE `playlist_items`")
            database.execSQL("ALTER TABLE `playlist_items_new` RENAME TO `playlist_items`")
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_items_episodeId` ON `playlist_items` (`episodeId`)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_items_audiobookId` ON `playlist_items` (`audiobookId`)"
            )
        }
    }
}
