package com.miradio.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.miradio.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateCheckResult {
    data class UpdateAvailable(val apkFile: File, val versionName: String?, val versionCode: Long) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Failure(val reason: String) : UpdateCheckResult()
}

/**
 * Actualización dentro de la propia app, sin pasar por Android Studio ni por
 * la web de GitHub: descarga el APK de depuración que publica el flujo de
 * GitHub Actions en la Release "latest-debug" y, si es más reciente que la
 * instalada, deja que el usuario la instale con un solo toque.
 *
 * Android no permite instalar en silencio una app fuera de Play Store: el
 * propio instalador del sistema pedirá confirmar la instalación (y, la
 * primera vez, permitir "instalar apps de origen desconocido" para esta
 * app). Eso no se puede evitar sin dispositivo rooteado; lo que sí evita
 * esta función es tener que abrir el navegador y buscar el enlace a mano.
 */
object AppUpdater {
    private const val APK_URL =
        "https://github.com/cazique/mi-radio-android/releases/download/latest-debug/app-debug.apk"

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        DiagnosticsLog.log(context, "AppUpdater", "checkForUpdate()")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        try {
            val request = Request.Builder().url(APK_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failure("HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext UpdateCheckResult.Failure("Respuesta vacía")
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "mi-radio-update.apk")
                body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }

                val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    ?: return@withContext UpdateCheckResult.Failure("No se ha podido leer el APK descargado")
                val downloadedVersionCode = PackageInfoCompat.getLongVersionCode(info)
                val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
                DiagnosticsLog.log(
                    context,
                    "AppUpdater",
                    "Descargado ${info.versionName} ($downloadedVersionCode), instalado ${BuildConfig.VERSION_NAME} ($currentVersionCode)",
                )
                if (downloadedVersionCode > currentVersionCode) {
                    UpdateCheckResult.UpdateAvailable(file, info.versionName, downloadedVersionCode)
                } else {
                    file.delete()
                    UpdateCheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "AppUpdater", "checkForUpdate falló", e)
            UpdateCheckResult.Failure(e.message ?: "Error de conexión")
        }
    }

    /** Lanza el instalador del sistema con el APK ya descargado. */
    fun installUpdate(context: Context, apkFile: File) {
        DiagnosticsLog.log(context, "AppUpdater", "installUpdate(${apkFile.name})")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
