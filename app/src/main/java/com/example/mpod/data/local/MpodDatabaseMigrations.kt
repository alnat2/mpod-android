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
}
