package com.example.eventmanagerapplication

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object EventSyncScheduler {
    const val WORK_NAME = "upload-all-events"
    const val FORCE_UPLOAD = "force_upload"

    fun enqueue(context: Context, forceUpload: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<EventSyncWorker>()
            .setInputData(workDataOf(FORCE_UPLOAD to forceUpload))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Append a follow-up when data changes during an in-flight upload. Each
        // worker reads the newest SQLite snapshot, coalescing any pending edits.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
