package com.iknalos.warpgo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences.autoConnectOnBoot(context)) return
        if (!WarpManager.isRegistered(context)) return

        // A previous manual connection must have granted VPN permission.
        // Android can only show the permission dialog from the foreground UI.
        if (VpnService.prepare(context) != null) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<AutoConnectWorker>()
            .setConstraints(constraints)
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            AutoConnectWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }
}
