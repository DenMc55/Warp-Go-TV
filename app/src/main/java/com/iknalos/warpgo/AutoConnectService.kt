package com.iknalos.warpgo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
 * Fire TV friendly boot auto-connect helper.
 *
 * BOOT_COMPLETED starts this as a foreground service immediately, which keeps
 * the process alive while Fire OS finishes bringing Wi-Fi/Ethernet up. The
 * service waits 40 seconds, then attempts WARP several times before giving up.
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
                .setContentText("Waiting to auto-connect WARP…")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // Fire TV often needs considerably longer than Android phones
                // before the network and VPN stack are fully ready after boot.
                delay(INITIAL_DELAY_MS)

                repeat(MAX_ATTEMPTS) { attempt ->
                    if (!AppPreferences.autoConnectOnBoot(applicationContext)) return@launch
                    if (!WarpManager.isRegistered(applicationContext)) return@launch
                    if (WarpManager.isUp(applicationContext)) return@launch

                    // Permission must have been granted by one manual connection.
                    if (VpnService.prepare(applicationContext) != null) return@launch

                    try {
                        WarpManager.connect(
                            applicationContext,
                            AppPreferences.selectedPort(applicationContext)
                        )
                        if (WarpManager.isUp(applicationContext)) return@launch
                    } catch (_: Exception) {
                        // Retry below. Fire OS may still be finalising networking.
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Warp Go TV auto-connect",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Warp Go TV alive briefly after boot while it reconnects WARP."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "warp_go_tv_boot"
        private const val NOTIFICATION_ID = 4500
        private const val INITIAL_DELAY_MS = 40_000L
        private const val RETRY_DELAY_MS = 10_000L
        private const val MAX_ATTEMPTS = 6
    }
}
