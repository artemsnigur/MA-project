package com.example.cityfixmaas.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cityfixmaas.CityFixApp
import java.util.concurrent.TimeUnit

/** Background check for notifications and expired tickets while the app is closed. */
class TicketCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        (applicationContext as CityFixApp).watcher.checkOnce()
        return Result.success()
    }

    companion object {
        /** Every 15 minutes (the WorkManager minimum), only when online. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TicketCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ticket-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
