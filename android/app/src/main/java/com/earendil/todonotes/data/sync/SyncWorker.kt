package com.earendil.todonotes.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodischer Background-Sync. Läuft alle 15 Min (WorkManager-Minimum),
 *  nur mit Netzwerkverbindung. Sync selbst bricht ab, wenn nicht konfiguriert. */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mgr = SyncManager(applicationContext)
        return if (mgr.sync()) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "todonotes-periodic-sync"

        /** Periodischen Sync einrichten (idempotent — mehrfach aufrufbar). */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
