package com.miradio.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.miradio.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
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
 *
 * OJO — límite real de este mecanismo: el APK que se descarga aquí está
 * firmado con `keystore/debug.keystore`, que vive en este mismo repositorio
 * público. Android exige que un "update" tenga la misma firma que la app ya
 * instalada, así que ese control del sistema sigue protegiendo frente a un
 * APK firmado con otra clave — pero cualquiera que se descargue este
 * repositorio tiene esa clave, así que en teoría podría firmar un APK propio
 * que sí pasaría ese control. Las comprobaciones de aquí (paquete y huella
 * de la firma antes de ofrecer instalar, límite de tamaño) son una capa de
 * defensa adicional, no una solución a esa raíz: cerrarla del todo exigiría
 * dejar de usar una clave de depuración pública para este canal, algo que
 * conviene decidir aparte (implicaría una reinstalación manual única para
 * quien ya tenga la app).
 */
object AppUpdater {
    private const val APK_URL =
        "https://github.com/cazique/mi-radio-android/releases/download/latest-debug/app-debug.apk"

    // Muy por encima de lo que pesará nunca este APK real (unas pocas
    // decenas de MB): solo para cortar una respuesta corrupta o inesperada
    // en vez de dejar que llene el almacenamiento del móvil sin límite.
    private const val MAX_APK_SIZE_BYTES = 200L * 1024 * 1024

    // Forzar HTTP/1.1: el CDN de descargas de GitHub a veces corta a media
    // petición con "stream was reset: REFUSED_STREAM" (visto varias veces
    // en registros reales), un fallo propio del multiplexado de HTTP/2. Con
    // HTTP/1.1 cada descarga va por su propia conexión y ese fallo concreto
    // desaparece.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        DiagnosticsLog.log(context, "AppUpdater", "checkForUpdate()")
        var lastError: Exception? = null
        // Hasta 3 intentos: un corte de conexión puntual no debería obligar
        // al usuario a darle varias veces a mano a "Buscar actualizaciones".
        repeat(3) { attempt ->
            try {
                return@withContext downloadAndCompare(context)
            } catch (e: Exception) {
                lastError = e
                DiagnosticsLog.logThrowable(context, "AppUpdater", "checkForUpdate falló (intento ${attempt + 1}/3)", e)
                if (attempt < 2) delay(1_000L * (attempt + 1))
            }
        }
        UpdateCheckResult.Failure(lastError?.message ?: "Error de conexión")
    }

    /** Cabecera del servidor (ETag o, si no la manda, Last-Modified) para el
     *  APK ya visto en la comprobación anterior: si no ha cambiado, ni
     *  siquiera hace falta pedir el cuerpo entero (varias decenas de MB)
     *  cada vez, solo para acabar concluyendo "sigue igual" como pasaba
     *  antes en casi todas las comprobaciones (cada 30 min). */
    private fun prefs(context: Context) = context.getSharedPreferences("app_updater", Context.MODE_PRIVATE)

    private fun downloadAndCompare(context: Context): UpdateCheckResult {
        val cachedTag = prefs(context).getString(KEY_LAST_ETAG, null)
        if (cachedTag != null) {
            val headRequest = Request.Builder().url(APK_URL).head().build()
            val unchanged = runCatching {
                client.newCall(headRequest).execute().use { head ->
                    head.isSuccessful && currentEtag(head) == cachedTag
                }
            }.getOrDefault(false)
            if (unchanged) return UpdateCheckResult.UpToDate
        }

        val request = Request.Builder().url(APK_URL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return UpdateCheckResult.Failure("HTTP ${response.code}")
            }
            val body = response.body ?: return UpdateCheckResult.Failure("Respuesta vacía")
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_APK_SIZE_BYTES) {
                return UpdateCheckResult.Failure("El APK descargado supera el tamaño máximo esperado")
            }

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "mi-radio-update.apk")
            var written = 0L
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        written += read
                        if (written > MAX_APK_SIZE_BYTES) {
                            file.delete()
                            return UpdateCheckResult.Failure("Descarga cancelada: supera el tamaño máximo esperado")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, SIGNATURE_FLAGS)
            if (info == null) {
                file.delete()
                return UpdateCheckResult.Failure("No se ha podido leer el APK descargado")
            }

            // El paquete descargado tiene que ser el mismo que el instalado
            // (mismo id de app): Android rechazaría igualmente instalarlo
            // como "actualización" si no lo fuera, pero mejor no llegar ni
            // a ofrecer el botón en ese caso.
            if (info.packageName != context.packageName) {
                file.delete()
                return UpdateCheckResult.Failure("Paquete inesperado en el APK descargado: ${info.packageName}")
            }

            // Firma distinta a la ya instalada: no debería poder pasar
            // nunca desde esta URL en concreto, pero si algún día lo hace
            // (o alguien intercepta o sustituye la respuesta), mejor
            // detectarlo aquí con un mensaje claro que dejar que el
            // instalador del sistema lo rechace con un error más críptico.
            if (!hasMatchingSignature(context, info)) {
                file.delete()
                return UpdateCheckResult.Failure("La firma del APK descargado no coincide con la instalada")
            }

            val downloadedVersionCode = PackageInfoCompat.getLongVersionCode(info)
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            DiagnosticsLog.log(
                context,
                "AppUpdater",
                "Descargado ${info.versionName} ($downloadedVersionCode), instalado ${BuildConfig.VERSION_NAME} ($currentVersionCode)",
            )

            return if (downloadedVersionCode > currentVersionCode) {
                // No se guarda el ETag aquí a propósito: hay una
                // actualización pendiente de instalar, así que la próxima
                // comprobación periódica debe seguir viéndola (y no
                // "olvidarla" solo porque el fichero remoto no ha cambiado
                // desde entonces).
                UpdateCheckResult.UpdateAvailable(file, info.versionName, downloadedVersionCode)
            } else {
                file.delete()
                currentEtag(response)?.let { tag -> prefs(context).edit().putString(KEY_LAST_ETAG, tag).apply() }
                UpdateCheckResult.UpToDate
            }
        }
    }

    private fun currentEtag(response: okhttp3.Response): String? =
        response.header("ETag") ?: response.header("Last-Modified")

    /** Compara la huella (SHA-256) de las firmas del APK descargado con las
     *  de la app ya instalada. GET_SIGNING_CERTIFICATES (API 28+) entiende
     *  la rotación de claves de la nueva firma v3; por debajo, GET_SIGNATURES
     *  (obsoleta pero la única disponible hasta minSdk 24). */
    @Suppress("DEPRECATION")
    private fun hasMatchingSignature(context: Context, downloadedInfo: PackageInfo): Boolean {
        val installedInfo = context.packageManager.getPackageInfo(context.packageName, SIGNATURE_FLAGS)
        val installedFingerprints = signatureFingerprints(installedInfo)
        val downloadedFingerprints = signatureFingerprints(downloadedInfo)
        if (installedFingerprints.isEmpty() || downloadedFingerprints.isEmpty()) {
            // No se ha podido leer ninguna firma de alguno de los dos lados
            // (no debería pasar): mejor no bloquear una actualización
            // legítima por un fallo de lectura, Android hará su propia
            // comprobación real al instalar de todos modos.
            return true
        }
        return installedFingerprints.intersect(downloadedFingerprints).isNotEmpty()
    }

    @Suppress("DEPRECATION")
    private fun signatureFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        } ?: return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
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

    private const val KEY_LAST_ETAG = "last_etag"
    private val SIGNATURE_FLAGS =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
}
