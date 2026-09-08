package com.iknalos.warpgo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Boot/startup reconnect helper.
 *
 * BOOT_COMPLETED starts this as a foreground service. It waits a minimum of
 * 20 seconds, then waits for Android to report a usable network before trying
 * WARP. Failed tunnel attempts are retried, but time spent waiting for Wi-Fi,
 * Ethernet or mobile data does not consume a retry.
 *
 * Startup policy:
 * - Auto-connect ON: always try to bring WARP up.
 * - Auto-connect OFF: restore the user's last manual Connect/Disconnect state.
 */
class AutoConnectService : Service() {

    private val serviceJob: Job = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Warp Go TV")
                .setContentText("Waiting to restore WARP…")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // Minimum boot delay. The actual attempt is also gated by network
                // availability, so a slow Fire TV/phone boot does not waste retries.
                delay(INITIAL_DELAY_MS)

                if (!AppPreferences.shouldRestoreConnectedState(applicationContext)) return@launch
                if (!WarpManager.isRegistered(applicationContext)) return@launch
                if (WarpManager.isUp(applicationContext)) return@launch

                // Permission must have been granted by one manual connection.
                if (VpnService.prepare(applicationContext) != null) return@launch

                repeat(MAX_ATTEMPTS) { attempt ->
                    if (!AppPreferences.shouldRestoreConnectedState(applicationContext)) return@launch
                    if (WarpManager.isUp(applicationContext)) return@launch

                    // Do not count a missing network as a failed WARP attempt.
                    if (!waitForUsableNetwork(NETWORK_WAIT_TIMEOUT_MS)) return@launch

                    try {
                        WarpManager.connect(
                            applicationContext,
                            AppPreferences.selectedPort(applicationContext)
                        )
                        if (WarpManager.isUp(applicationContext)) return@launch
                    } catch (_: Exception) {
                        // Tunnel setup can still fail while Android/Fire OS settles.
                    }

                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun waitForUsableNetwork(timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited <= timeoutMs) {
            if (hasUsableNetwork(applicationContext)) return true
            delay(NETWORK_POLL_MS)
            waited += NETWORK_POLL_MS
        }
        return false
    }

    private fun hasUsableNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Warp Go TV auto-connect",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Warp Go TV alive briefly after boot while it restores WARP."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "warp_go_tv_boot"
        private const val NOTIFICATION_ID = 4500
        private const val INITIAL_DELAY_MS = 20_000L
        private const val RETRY_DELAY_MS = 10_000L
        private const val MAX_ATTEMPTS = 6
        private const val NETWORK_POLL_MS = 2_000L
        private const val NETWORK_WAIT_TIMEOUT_MS = 90_000L
    }
}
