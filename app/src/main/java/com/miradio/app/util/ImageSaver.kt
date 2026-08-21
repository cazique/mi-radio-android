package com.miradio.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.io.File
import java.io.FileOutputStream

/**
 * Descarga y guarda/comparte imágenes sueltas (portadas de periódico, de
 * momento) sin depender de que Coil ya la tenga en su caché de Compose: se
 * pide aparte con el mismo ImageLoader de la app para poder quedarse con el
 * Bitmap final.
 */
object ImageSaver {

    /** Muchos sitios de imágenes (kiosko.net incluido, al parecer) bloquean
     *  con 403 las peticiones que no parezcan venir de un navegador
     *  navegando su propia web ("hotlink protection"): sin un User-Agent de
     *  navegador y un Referer de su dominio, la imagen no carga aunque la
     *  URL sea correcta. Mismo tipo de problema que ya se vio con el RSS de
     *  La Razón. */
    val HOTLINK_PROTECTED_IMAGE_HEADERS: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mi Radio App) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
        .add("Referer", "https://kiosko.net/")
        .build()

    suspend fun downloadBitmap(context: Context, url: String, headers: Headers? = null): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .apply { if (headers != null) headers(headers) }
            // Bitmap "de software": el guardado/compartido necesita poder
            // leer los píxeles directamente, cosa que un bitmap hardware
            // (el que usa Coil normalmente por eficiencia) no permite.
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request).drawable
        (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }

    /** Guarda en la galería del dispositivo (álbum "Mi Radio"). En Android 10+
     *  no hace falta permiso alguno para insertar contenido propio en
     *  MediaStore; en versiones anteriores sí (ver [needsWritePermission]). */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mi Radio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mi Radio")
                if (!picturesDir.exists()) picturesDir.mkdirs()
                val file = File(picturesDir, "$displayName.jpg")
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                true
            }
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(context, "ImageSaver", "saveToGallery falló", e)
            false
        }
    }

    fun needsWritePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Comparte por cualquier app (WhatsApp, correo, etc.): se escribe una
     *  copia temporal en caché y se cede mediante FileProvider, sin exponer
     *  ninguna carpeta de la app directamente. */
    suspend fun shareImage(context: Context, bitmap: Bitmap, displayName: String) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "$displayName.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, null)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
