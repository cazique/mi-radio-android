package com.miradio.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Registro de diagnóstico muy simple, en un archivo de texto dentro del
 * almacenamiento privado de la app (no requiere permisos). Sirve para poder
 * ver qué ha pasado justo antes de un cierre inesperado sin necesidad de
 * conectar el móvil a un ordenador con adb: en Ajustes > Diagnóstico se
 * puede copiar el contenido para pegarlo donde haga falta.
 */
object DiagnosticsLog {
    private const val FILE_NAME = "miradio_diagnostico.log"
    private const val MAX_BYTES = 200 * 1024

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun log(context: Context, tag: String, message: String) {
        runCatching {
            val line = "${dateFormat.format(System.currentTimeMillis())} [$tag] $message\n"
            logFile(context).appendText(line)
            trimIfNeeded(context)
        }
    }

    @Synchronized
    fun logThrowable(context: Context, tag: String, message: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        log(context, tag, "$message: ${error.message}\n$sw")
    }

    /** Instala un manejador que registra cualquier excepción no capturada
     *  antes de dejar que el sistema gestione el cierre como siempre (no se
     *  oculta el crash, solo se deja constancia de por qué ha pasado). */
    fun installUncaughtExceptionLogger(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { logThrowable(appContext, "FATAL", "Excepción no capturada en ${thread.name}", throwable) }
            Log.e("DiagnosticsLog", "Excepción no capturada", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readAll(context: Context): String =
        runCatching { logFile(context).takeIf { it.exists() }?.readText() }.getOrNull()
            ?: "Todavía no hay nada registrado."

    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    private fun trimIfNeeded(context: Context) {
        val file = logFile(context)
        if (file.length() <= MAX_BYTES) return
        val lines = file.readLines()
        val keep = lines.takeLast(lines.size / 2)
        file.writeText(keep.joinToString("\n", postfix = "\n"))
    }
}
