package com.miradio.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "miradio.db",
                ).build().also { instance = it }
            }
    }
}
