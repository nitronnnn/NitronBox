package com.nitronbox.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ProviderProfileEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NitronBoxDatabase : RoomDatabase() {
    abstract fun dao(): NitronBoxDao

    companion object {
        @Volatile private var instance: NitronBoxDatabase? = null

        /** v2 adds persisted provider profiles and per-message failure details. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        serializedProfile TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_provider_profiles_updatedAtEpochMillis ON provider_profiles (updatedAtEpochMillis)")
                db.execSQL("ALTER TABLE messages ADD COLUMN errorText TEXT")
            }
        }

        /** v3 tracks wall-clock generation duration for tok/s and response-time stats. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN generationDurationMillis INTEGER")
            }
        }

        /** v4 marks Creator conversations with their project folder (SAF tree URI). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN folderUri TEXT")
            }
        }

        /** v5 stores the model's reasoning stream for the collapsible thinking section. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT")
            }
        }

        fun create(context: Context): NitronBoxDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NitronBoxDatabase::class.java,
                "nitronbox.db",
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
