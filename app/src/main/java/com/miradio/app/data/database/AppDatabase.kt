package com.miradio.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Añade la tabla de alarmas despertador. Migración real (no
 * fallbackToDestructiveMigration): StationEntity no cambia en absoluto,
 * así que borrar la base entera solo para añadir esta tabla nueva se
 * cargaría sin necesidad las emisoras y favoritas de quien ya tuviera la
 * app instalada.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `alarms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `repeatDays` TEXT NOT NULL,
                `stationId` TEXT NOT NULL,
                `stationName` TEXT NOT NULL,
                `stationStreamUrl` TEXT NOT NULL,
                `stationLogoUrl` TEXT,
                `startVolume` REAL NOT NULL,
                `volumeIncreasing` INTEGER NOT NULL,
                `fadeInSeconds` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

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
@Database(entities = [StationEntity::class, AlarmEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao
    abstract fun alarmDao(): AlarmDao

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
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
