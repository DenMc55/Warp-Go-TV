package com.iknalos.warpgo

import android.content.Context
import android.net.VpnService
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Connects WARP after boot once Android reports that a network is available.
 * WorkManager gives Fire TV time to finish bringing Wi-Fi/Ethernet online and
 * retries transient failures instead of making one fixed-delay attempt.
 */
class AutoConnectWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        if (!AppPreferences.autoConnectOnBoot(applicationContext)) return Result.success()
        if (!WarpManager.isRegistered(applicationContext)) return Result.success()

        // Permission must already have been granted by one manual connection.
        if (VpnService.prepare(applicationContext) != null) return Result.failure()

        if (WarpManager.isUp(applicationContext)) return Result.success()

        return try {
            WarpManager.connect(
                applicationContext,
                AppPreferences.selectedPort(applicationContext)
            )
            if (WarpManager.isUp(applicationContext)) {
                Result.success()
            } else if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "warp_go_tv_auto_connect"
        private const val MAX_RETRIES = 6
    }
}
