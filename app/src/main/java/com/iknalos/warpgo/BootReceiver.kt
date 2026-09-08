package com.iknalos.warpgo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Auto-connect ON always requests WARP after boot. With Auto-connect OFF,
        // restore the user's last manual Connect/Disconnect choice.
        if (!AppPreferences.shouldRestoreConnectedState(context)) return
        if (!WarpManager.isRegistered(context)) return

        // Android can only display the VPN permission prompt from the UI.
        // Startup restore therefore requires one successful manual connection first.
        if (VpnService.prepare(context) != null) return

        val serviceIntent = Intent(context, AutoConnectService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
