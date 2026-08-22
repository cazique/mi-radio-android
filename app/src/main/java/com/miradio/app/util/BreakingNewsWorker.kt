package com.miradio.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.miradio.app.MainActivity
import com.miradio.app.R
import com.miradio.app.RadioApp
import com.miradio.app.domain.model.NewsCategory
import com.miradio.app.domain.model.NewsSource
import com.miradio.app.domain.model.PresetNewsSources
import kotlinx.coroutines.flow.first

private const val CHANNEL_ID = "miradio_breaking_news"

/** Los medios españoles marcan sus titulares urgentes anteponiendo
 *  literalmente "Última hora" (p. ej. "Última hora: ..."); es la única
 *  señal fiable disponible en un RSS normal para distinguir un aviso
 *  urgente de una noticia cualquiera sin inventar un criterio propio. */
private const val BREAKING_PREFIX = "última hora"

/**
 * Comprobación periódica (ver [BreakingNewsScheduler]) de las fuentes de
 * noticias activas: si aparece un titular de última hora que no se había
 * avisado ya, manda una notificación. Vive como WorkManager (no un servicio
 * en primer plano) para respetar Doze/ahorro de batería sin tener que pedir
 * ningún permiso extra.
 */
class BreakingNewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as RadioApp).container
        val prefs = container.preferencesRepository
        if (!prefs.breakingNewsAlertsEnabled.first()) return Result.success()

        val copeSources = NewsCategory.entries.map { NewsSource.fromCategory(it) }
        val presetSources = PresetNewsSources.all.filter { it.id in prefs.enabledPresetNewsSources.first() }
        val customSources = prefs.customNewsSources.first().map { NewsSource.fromCustom(it) }
        val sources = copeSources + presetSources + customSources

        val alreadyNotified = prefs.notifiedBreakingNewsLinks.first()

        for (source in sources) {
            val articles = container.newsRepository.fetchFeed(source.feedUrl, source.id).getOrElse { emptyList() }
            val breaking = articles.filter {
                it.title.trim().startsWith(BREAKING_PREFIX, ignoreCase = true) && it.link !in alreadyNotified
            }
            breaking.forEach { article ->
                showNotification(applicationContext, article.title, source.label, article.link)
                prefs.markBreakingNewsNotified(article.link)
            }
        }
        return Result.success()
    }

    private fun showNotification(context: Context, title: String, sourceLabel: String, link: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avisos de última hora",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Avisa cuando hay una noticia urgente en las fuentes activas" }
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            link.hashCode(),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(sourceLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(link.hashCode(), notification) }
            .onFailure { DiagnosticsLog.logThrowable(context, "BreakingNewsWorker", "No se pudo mostrar el aviso de última hora", it) }
    }
}
