package com.iknalos.warpgo

import android.content.Context

object AppPreferences {
    private const val PREFS = "warp_go_tv_prefs"
    private const val KEY_AUTO_CONNECT = "auto_connect_boot"
    private const val KEY_PORT = "selected_port"
    private const val KEY_UI_MODE = "ui_mode"
    private const val KEY_LAST_MANUAL_CONNECTED = "last_manual_connected"

    const val MODE_TV = "tv"
    const val MODE_MOBILE = "mobile"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoConnectOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONNECT, false)

    fun setAutoConnectOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    /**
     * Remembers the user's most recent explicit Connect/Disconnect choice.
     * Automatic reconnects never change this value.
     */
    fun lastManualConnected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_MANUAL_CONNECTED, false)

    fun setLastManualConnected(context: Context, connected: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_MANUAL_CONNECTED, connected).apply()
    }

    /**
     * Desired startup state:
     * - Auto-connect ON always requests WARP ON.
     * - Auto-connect OFF restores the user's last manual state.
     */
    fun shouldRestoreConnectedState(context: Context): Boolean =
        autoConnectOnBoot(context) || lastManualConnected(context)

    fun selectedPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, 4500)

    fun setSelectedPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port).apply()
    }

    fun hasUiMode(context: Context): Boolean = prefs(context).contains(KEY_UI_MODE)

    fun uiMode(context: Context): String =
        prefs(context).getString(KEY_UI_MODE, MODE_TV) ?: MODE_TV

    fun setUiMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_UI_MODE, mode).apply()
    }
}
