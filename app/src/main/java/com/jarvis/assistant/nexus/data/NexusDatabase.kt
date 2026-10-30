package com.jarvis.assistant.nexus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NexusPairedDeviceEntity::class,
        NexusTransferEntity::class,
        NexusAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {

    abstract fun nexusDao(): NexusDao

    companion object {
        private const val DATABASE_NAME = "jarvis_nexus.db"

        @Volatile
        private var instance: NexusDatabase? = null

        fun getInstance(context: Context): NexusDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexusDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
