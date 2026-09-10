package com.iknalos.warpgo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var busy = false
    private var isTvMode = true

    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var toggleButton: Button
    private lateinit var portGroup: RadioGroup
    private lateinit var port4500: RadioButton
    private lateinit var port2408: RadioButton
    private lateinit var port500: RadioButton
    private lateinit var autoConnectSwitch: MaterialSwitch
    private lateinit var resetButton: Button

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                doConnect()
            } else {
                setStatus("VPN permission denied", StatusState.ERROR)
                setBusy(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AppPreferences.hasUiMode(this)) {
            showFirstRunSetup()
            return
        }

        showSelectedInterface()
    }

    override fun onResume() {
        super.onResume()
        if (::toggleButton.isInitialized) {
            refreshState()
            if (isTvMode) focusToggleButton()
            // Second-chance restore: if Android/Fire OS blocked the background
            // boot attempt, opening Warp Go gives the desired ON state another
            // chance without showing the VPN permission dialog.
            maybeRestoreDesiredState()
        }
    }

    private fun showFirstRunSetup() {
        setContentView(R.layout.activity_setup)

        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        val modeTv = findViewById<RadioButton>(R.id.modeTv)
        val modeMobile = findViewById<RadioButton>(R.id.modeMobile)
        val okButton = findViewById<Button>(R.id.modeOkButton)

        // Deliberately default to TV. On a television the mobile layout can look
        // usable while hiding TV-only controls below the fold; the reverse is obvious.
        modeTv.isChecked = true
        modeTv.requestFocus()

        okButton.setOnClickListener {
            val mode = if (modeGroup.checkedRadioButtonId == modeMobile.id) {
                AppPreferences.MODE_MOBILE
            } else {
                AppPreferences.MODE_TV
            }
            AppPreferences.setUiMode(this, mode)
            showSelectedInterface()
        }
    }

    private fun showSelectedInterface() {
        isTvMode = AppPreferences.uiMode(this) == AppPreferences.MODE_TV
        setContentView(if (isTvMode) R.layout.activity_tv else R.layout.activity_mobile)
        bindMainViews()
        wireMainControls()
        restorePreferences()
        refreshState()
        if (isTvMode) focusToggleButton()
    }

    private fun bindMainViews() {
        statusText = findViewById(R.id.statusText)
        progress = findViewById(R.id.progress)
        toggleButton = findViewById(R.id.toggleButton)
        portGroup = findViewById(R.id.portGroup)
        port4500 = findViewById(R.id.port4500)
        port2408 = findViewById(R.id.port2408)
        port500 = findViewById(R.id.port500)
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch)
        resetButton = findViewById(R.id.resetButton)
    }

    private fun wireMainControls() {
        toggleButton.setOnClickListener { onToggle() }
        portGroup.setOnCheckedChangeListener { _, _ ->
            AppPreferences.setSelectedPort(this, selectedPort())
        }
        autoConnectSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setAutoConnectOnBoot(this, checked)
        }
        resetButton.setOnClickListener {
            WarpManager.resetRegistration(this)
            setStatus("Registration cleared", StatusState.DISCONNECTED)
        }

        if (isTvMode) {
            toggleButton.setOnFocusChangeListener { _, hasFocus ->
                updateToggleFocusStyle(hasFocus)
            }
        }
    }

    private fun restorePreferences() {
        when (AppPreferences.selectedPort(this)) {
            2408 -> port2408.isChecked = true
            500 -> port500.isChecked = true
            else -> port4500.isChecked = true
        }
        autoConnectSwitch.isChecked = AppPreferences.autoConnectOnBoot(this)
    }

    private fun refreshState() {
        val up = WarpManager.isUp(this)
        toggleButton.text = getString(if (up) R.string.disconnect else R.string.connect)
        if (up) {
            setStatus("Connected  ·  port ${selectedPort()}", StatusState.CONNECTED)
        } else {
            setStatus("Disconnected", StatusState.DISCONNECTED)
        }
    }

    private fun selectedPort(): Int = when (portGroup.checkedRadioButtonId) {
        port4500.id -> 4500
        port500.id -> 500
        else -> 2408
    }

    private fun onToggle() {
        if (busy) return
        if (WarpManager.isUp(this)) {
            // Remember the user's explicit choice, independently of the
            // Auto-connect switch.
            AppPreferences.setLastManualConnected(this, false)
            disconnect()
        } else {
            AppPreferences.setLastManualConnected(this, true)
            setBusy(true)
            setStatus("Connecting…", StatusState.CONNECTING)
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPermission.launch(intent)
            } else {
                doConnect()
            }
        }
    }


    private fun maybeRestoreDesiredState() {
        if (busy) return
        if (!AppPreferences.shouldRestoreConnectedState(this)) return
        if (!WarpManager.isRegistered(this)) return
        if (WarpManager.isUp(this)) return
        if (!hasUsableNetwork()) return

        // Never launch the Android VPN permission UI automatically. One manual
        // connection must have granted permission previously.
        if (VpnService.prepare(this) != null) return

        setBusy(true)
        setStatus("Restoring connection…", StatusState.CONNECTING)
        val port = AppPreferences.selectedPort(this)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WarpManager.connect(applicationContext, port)
                }
                refreshState()
            } catch (_: Exception) {
                // Leave the app usable. The boot foreground service has its own
                // retry loop; this path is deliberately just an extra chance.
                refreshState()
            } finally {
                setBusy(false)
                refreshButtonOnly()
                if (isTvMode) focusToggleButton()
            }
        }
    }

    private fun hasUsableNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun doConnect() {
        val port = selectedPort()
        AppPreferences.setSelectedPort(this, port)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WarpManager.connect(applicationContext, port) }
                setStatus("Connected  ·  port $port", StatusState.CONNECTED)
            } catch (e: Exception) {
                setStatus("Failed: ${e.message ?: "unknown error"}", StatusState.ERROR)
            } finally {
                setBusy(false)
                refreshButtonOnly()
                if (isTvMode) focusToggleButton()
            }
        }
    }

    private fun disconnect() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WarpManager.disconnect(applicationContext) }
                setStatus("Disconnected", StatusState.DISCONNECTED)
            } catch (e: Exception) {
                setStatus("Error: ${e.message ?: "unknown error"}", StatusState.ERROR)
            } finally {
                setBusy(false)
                refreshButtonOnly()
                if (isTvMode) focusToggleButton()
            }
        }
    }

    private fun refreshButtonOnly() {
        toggleButton.text = getString(
            if (WarpManager.isUp(this)) R.string.disconnect else R.string.connect
        )
    }

    private fun setBusy(value: Boolean) {
        busy = value
        // Do not disable the button in TV mode: disabling a focused view makes
        // Fire TV immediately move focus to the next available control.
        toggleButton.isEnabled = true
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun focusToggleButton() {
        toggleButton.postDelayed({
            toggleButton.requestFocus()
            updateToggleFocusStyle(true)
        }, 250L)
    }

    private fun updateToggleFocusStyle(hasFocus: Boolean) {
        val color = getColor(if (hasFocus) R.color.focus_yellow else R.color.warp_orange)
        toggleButton.backgroundTintList = ColorStateList.valueOf(color)
        toggleButton.setTextColor(
            getColor(if (hasFocus) R.color.focus_text_dark else R.color.text_primary)
        )
    }

    private fun setStatus(text: String, state: StatusState) {
        val dotColor = when (state) {
            StatusState.CONNECTED -> getColor(R.color.status_green)
            StatusState.CONNECTING -> getColor(R.color.status_amber)
            StatusState.DISCONNECTED, StatusState.ERROR -> getColor(R.color.status_red)
        }
        val display = SpannableString("●  $text")
        display.setSpan(
            ForegroundColorSpan(dotColor),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        statusText.text = display
        statusText.setTextColor(Color.WHITE)
    }

    private enum class StatusState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
}
