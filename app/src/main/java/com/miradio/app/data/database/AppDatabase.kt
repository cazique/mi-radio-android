package com.miradio.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StationEntity::class], version = 2, exportSchema = false)
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
                )
                    // La app está en desarrollo activo y el catálogo se puede
                    // volver a poblar en cualquier momento (seed + sincronización
                    // remota), así que no hace falta escribir migraciones
                    // formales para cada cambio de esquema todavía.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
