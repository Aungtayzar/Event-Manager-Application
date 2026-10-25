package com.example.eventmanagerapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.net.URI
import org.json.JSONException

class EventSyncWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = EventDatabaseHelper(applicationContext).use { database ->
        try {
            val state = database.syncState()
            if (!state.enabled || (!state.needsUpload && !inputData.getBoolean(EventSyncScheduler.FORCE_UPLOAD, false))) {
                return@use Result.success()
            }
            if (requiresLocalNetwork() && ContextCompat.checkSelfPermission(applicationContext,
                    Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                database.recordUploadError(applicationContext.getString(R.string.upload_local_permission))
                return@use Result.failure()
            }
            if (!hasNetwork(applicationContext)) {
                database.recordUploadError(applicationContext.getString(R.string.upload_waiting_network))
                return@use Result.retry()
            }
            val api = EventUploadClient()
            api.checkService()
            val snapshot = database.uploadSnapshot()
            if (isStopped) return@use Result.retry()
            api.upload(snapshot)
            database.markUploaded(snapshot.revision)
            Result.success()
        } catch (exception: EventUploadException) {
            database.recordUploadError(exception.message.orEmpty())
            if (exception.retryable) Result.retry() else Result.failure()
        } catch (_: IOException) {
            database.recordUploadError(applicationContext.getString(R.string.upload_connection_failed))
            Result.retry()
        } catch (_: IllegalArgumentException) {
            database.recordUploadError(applicationContext.getString(R.string.upload_configuration_error))
            Result.failure()
        } catch (_: JSONException) {
            database.recordUploadError(applicationContext.getString(R.string.upload_invalid_data))
            Result.failure()
        } catch (_: SQLiteException) {
            // Retain local changes and retry if the local DB is temporarily busy.
            Result.retry()
        }
    }

    companion object {
        fun hasNetwork(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }

        fun requiresLocalNetwork(): Boolean {
            val host = URI(BuildConfig.EVENT_API_BASE_URL).host.orEmpty().lowercase()
            val parts = host.split('.').mapNotNull { it.toIntOrNull() }
            return host == "localhost" || host.endsWith(".local") || !host.contains('.') ||
                (parts.size == 4 && (parts[0] == 10 || parts[0] == 127 ||
                    (parts[0] == 192 && parts[1] == 168) ||
                    (parts[0] == 172 && parts[1] in 16..31))) || host.contains(':')
        }
    }
}
