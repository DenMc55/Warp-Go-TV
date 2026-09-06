package com.iknalos.warpgo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences.autoConnectOnBoot(context)) return
        if (!WarpManager.isRegistered(context)) return

        // Android only allows silent reconnect if the user has already granted
        // this app VPN permission during a normal manual connection.
        if (VpnService.prepare(context) != null) return

        val pendingResult = goAsync()
        Thread {
            try {
                // Give Wi-Fi / Ethernet a moment to come up after boot.
                Thread.sleep(5000)
                if (!WarpManager.isUp(context)) {
                    WarpManager.connect(context.applicationContext, AppPreferences.selectedPort(context))
                }
            } catch (_: Exception) {
                // The next manual launch can reconnect if the network was not
                // ready yet. Avoid crashing the boot receiver.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
