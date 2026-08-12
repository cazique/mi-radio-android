package com.miradio.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema = true: cada versión deja un JSON histórico en app/schemas/
// (ver room.schemaLocation en app/build.gradle.kts), imprescindible para
// poder escribir y probar migraciones reales de aquí en adelante.
//
// *** ANTES DE CAMBIAR StationEntity (añadir/quitar/renombrar una columna) ***
// 1. Sube "version" en uno.
// 2. Añade una Migration(version_anterior, version_nueva) real con el SQL
//    exacto (ALTER TABLE...) y regístrala con .addMigrations(...) abajo.
// 3. Solo entonces se puede plantear quitar fallbackToDestructiveMigration.
// Sin una Migration real para el salto de versión, fallbackToDestructiveMigration
// BORRA la base de datos entera del usuario (favoritas y emisoras añadidas a
// mano incluidas) en vez de fallar. Se mantiene por ahora porque quitarlo sin
// tener ninguna migración escrita convertiría cualquier cambio de esquema
// futuro en un cierre inmediato de la app para todo el mundo en vez de una
// pérdida de datos silenciosa; ninguna de las dos es aceptable a la larga,
// pero hay que resolverlo escribiendo la migración correspondiente, no aquí.
@Database(entities = [StationEntity::class], version = 3, exportSchema = true)
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
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
