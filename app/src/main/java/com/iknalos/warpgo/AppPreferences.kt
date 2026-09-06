package com.iknalos.warpgo

import android.content.Context

object AppPreferences {
    private const val PREFS = "warp_go_tv_prefs"
    private const val KEY_AUTO_CONNECT = "auto_connect_boot"
    private const val KEY_PORT = "selected_port"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoConnectOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONNECT, false)

    fun setAutoConnectOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun selectedPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, 4500)

    fun setSelectedPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port).apply()
    }
}
