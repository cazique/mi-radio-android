package com.miradio.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "breaking_news_alerts"

/** (Des)programa la comprobación periódica de [BreakingNewsWorker]. 30 min
 *  es un término medio razonable entre "avisar pronto" y no despertar el
 *  móvil más de la cuenta; WorkManager no permite bajar de 15 min de todos
 *  modos. */
object BreakingNewsScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<BreakingNewsWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
