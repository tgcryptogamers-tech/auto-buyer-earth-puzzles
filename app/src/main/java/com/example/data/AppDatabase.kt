package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppConfiguration::class, PurchaseRecord::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configurationDao(): ConfigurationDao
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_configuration SET enableActualBuying = 1 WHERE id = 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_configuration ADD COLUMN buyPuzzles INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_configuration ADD COLUMN buyLands INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE app_configuration SET " +
                        "buyPuzzles = CASE WHEN buyTarget = 'Lands' THEN 0 ELSE 1 END, " +
                        "buyLands = CASE WHEN buyTarget = 'Lands' THEN 1 ELSE 0 END"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_configuration SET scanIntervalMs = 20 WHERE scanIntervalMs >= 150")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_configuration SET scanIntervalMs = 0 WHERE scanIntervalMs <= 200")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_configuration SET tabSwitchIntervalMs = 430 WHERE tabSwitchIntervalMs < 150")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "earth_puzzles_bot_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
