package com.miradio.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Registro de diagnóstico muy simple, en un archivo de texto dentro del
 * almacenamiento privado de la app (no requiere permisos). Sirve para poder
 * ver qué ha pasado justo antes de un cierre inesperado sin necesidad de
 * conectar el móvil a un ordenador con adb: en Ajustes > Diagnóstico se
 * puede copiar el contenido para pegarlo donde haga falta.
 */
/** Nivel de severidad de una línea del registro, para poder distinguir de
 *  un vistazo un aviso sin importancia de un fallo real al leer el log. */
enum class LogLevel { INFO, WARN, ERROR }

object DiagnosticsLog {
    private const val FILE_NAME = "miradio_diagnostico.log"
    private const val MAX_BYTES = 200 * 1024

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // OJO: el archivo vive en un subdirectorio propio ("diagnostics/"), no
    // suelto en la raíz de filesDir. Con path="." en file_paths.xml (como
    // estaba antes), FileProvider.getUriForFile() lanzaba
    // StringIndexOutOfBoundsException al compartir el registro: al
    // canonicalizar una raíz declarada como "." acaba coincidiendo
    // exactamente con filesDir, y el cálculo interno de FileProvider
    // (path.substring(rootPath.length() + 1)) se sale de rango porque no
    // sobra ni un carácter para el nombre del archivo. Con un
    // subdirectorio real (mismo patrón que "updates/" para el APK
    // descargado, que nunca ha dado este problema) la raíz declarada
    // siempre queda estrictamente más corta que la ruta del archivo.
    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics").apply { mkdirs() }.let { File(it, FILE_NAME) }

    @Synchronized
    fun log(context: Context, tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        runCatching {
            val line = "${dateFormat.format(System.currentTimeMillis())} $level [$tag] $message\n"
            logFile(context).appendText(line)
            trimIfNeeded(context)
        }
    }

    /** Para algo que no ha roto nada pero conviene saber que ha pasado
     *  (p. ej. un reintento, un valor inesperado que se ha podido corregir
     *  solo). No confundir con [logThrowable], que es para fallos reales. */
    fun logWarning(context: Context, tag: String, message: String) {
        log(context, tag, message, LogLevel.WARN)
    }

    @Synchronized
    fun logThrowable(context: Context, tag: String, message: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        log(context, tag, "$message: ${error.message}\n$sw", LogLevel.ERROR)
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

    /**
     * No hay servidor detrás de esta app (es un APK suelto, sin backend), así
     * que un "envío automático" silencioso no es posible sin inventar una
     * infraestructura que no existe. Lo más cercano y honesto: un botón que
     * abre el selector de compartir de Android (correo, WhatsApp, lo que
     * sea) con el registro ya adjunto, en un solo toque en vez de copiar y
     * pegar a mano.
     */
    private val uploadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Sube el registro entero, tal cual, con un POST de texto plano a una
     * URL que cada usuario configura por su cuenta en Ajustes > Diagnóstico
     * (un servidor o webhook propio: aquí no hay ninguno nuestro al que
     * mandarlo). Sirve para no depender de abrir el selector de compartir a
     * mano cada vez que la app se cierra sola.
     */
    suspend fun uploadLog(context: Context, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = readAll(context).toRequestBody("text/plain; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            uploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            }
        }.onFailure { logThrowable(context, "DiagnosticsLog", "uploadLog falló", it) }
    }

    fun shareIntent(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile(context))
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Registro de Radio Dari")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Si la app se cerró de forma inesperada hace poco (registrado por
     * [installUncaughtExceptionLogger] justo antes de que el sistema matara
     * el proceso), lo detecta para poder ofrecer compartir el registro en
     * cuanto se vuelve a abrir la app, en vez de que el usuario tenga que
     * acordarse de ir a Ajustes por su cuenta.
     */
    @Synchronized
    fun hadRecentCrash(context: Context, withinMillis: Long = 5 * 60_000L): Boolean {
        val lastFatalAt = runCatching {
            logFile(context).takeIf { it.exists() }
                ?.readLines()
                ?.lastOrNull { it.contains("[FATAL]") }
                ?.let { line -> dateFormat.parse(line.take(19))?.time }
        }.getOrNull() ?: return false
        return System.currentTimeMillis() - lastFatalAt in 0..withinMillis
    }

    private fun trimIfNeeded(context: Context) {
        val file = logFile(context)
        if (file.length() <= MAX_BYTES) return
        val lines = file.readLines()
        val keep = lines.takeLast(lines.size / 2)
        file.writeText(keep.joinToString("\n", postfix = "\n"))
    }
}
