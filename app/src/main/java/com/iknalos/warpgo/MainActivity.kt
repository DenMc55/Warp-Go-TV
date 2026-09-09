package com.iknalos.warpgo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private lateinit var autoConnectSwitch: CompoundButton
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

    @Deprecated("Legacy API-25 VPN permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LEGACY_VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                doConnect()
            } else {
                setStatus("VPN permission denied", StatusState.ERROR)
                setBusy(false)
            }
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
        if (isLegacyFireOs()) {
            showLegacyFirstRunSetup()
            return
        }

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

    private fun showLegacyFirstRunSetup() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(40), dp(40), dp(40))
            setBackgroundColor(Color.rgb(4, 35, 63))
        }

        root.addView(TextView(this).apply {
            text = "Warp Go TV"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Choose interface"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val modeTv = RadioButton(this).apply {
            id = View.generateViewId()
            text = "TV"
            textSize = 22f
            setTextColor(Color.WHITE)
            isChecked = true
        }
        val modeMobile = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Mobile"
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        modeGroup.addView(modeTv)
        modeGroup.addView(modeMobile)
        root.addView(modeGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(28)
        })

        val okButton = Button(this).apply {
            text = "OK"
            textSize = 20f
            isAllCaps = false
        }
        root.addView(okButton, LinearLayout.LayoutParams(dp(260), dp(64)).apply {
            topMargin = dp(28)
        })

        setContentView(root)
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

        if (isLegacyFireOs()) {
            buildLegacyMainInterface()
        } else {
            setContentView(if (isTvMode) R.layout.activity_tv else R.layout.activity_mobile)
            bindMainViews()
        }

        wireMainControls()
        restorePreferences()
        refreshState()
        if (isTvMode) focusToggleButton()
    }

    private fun buildLegacyMainInterface() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(36), dp(28), dp(36), dp(40))
            setBackgroundColor(Color.rgb(4, 35, 63))
        }

        content.addView(TextView(this).apply {
            text = if (isTvMode) "Warp Go TV" else "Warp Go"
            textSize = if (isTvMode) 30f else 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(TextView(this).apply {
            text = "Cloudflare WARP tunnel"
            textSize = 17f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })

        statusText = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        content.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24)
        })

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        content.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            topMargin = dp(8)
        })

        toggleButton = Button(this).apply {
            text = "Connect"
            textSize = 22f
            isAllCaps = false
            isFocusable = true
        }
        content.addView(toggleButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)).apply {
            topMargin = dp(12)
        })

        content.addView(TextView(this).apply {
            text = "Connection port"
            textSize = 20f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(28)
        })

        content.addView(TextView(this).apply {
            text = "Change only if your network blocks WARP."
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })

        portGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        port4500 = legacyPortButton("4500 (recommended)")
        port2408 = legacyPortButton("2408 (WARP default)")
        port500 = legacyPortButton("500")
        portGroup.addView(port4500)
        portGroup.addView(port2408)
        portGroup.addView(port500)
        content.addView(portGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        autoConnectSwitch = Switch(this).apply {
            text = "Auto-connect on boot"
            textSize = 18f
            setTextColor(Color.WHITE)
            isFocusable = true
        }
        content.addView(autoConnectSwitch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(22)
        })

        content.addView(TextView(this).apply {
            text = "Requires one manual connection first."
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })

        resetButton = Button(this).apply {
            text = "Reset WARP account"
            textSize = 18f
            isAllCaps = false
            isFocusable = true
        }
        content.addView(resetButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(22)
        })

        content.addView(TextView(this).apply {
            text = "Port changes take effect on the next connection."
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
    }

    private fun legacyPortButton(label: String): RadioButton = RadioButton(this).apply {
        id = View.generateViewId()
        text = label
        textSize = 18f
        setTextColor(Color.WHITE)
        isFocusable = true
    }

    private fun isLegacyFireOs(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bindMainViews() {
        statusText = findViewById(R.id.statusText)
        progress = findViewById(R.id.progress)
        toggleButton = findViewById(R.id.toggleButton)
        portGroup = findViewById(R.id.portGroup)
        port4500 = findViewById(R.id.port4500)
        port2408 = findViewById(R.id.port2408)
        port500 = findViewById(R.id.port500)
        autoConnectSwitch = findViewById<CompoundButton>(R.id.autoConnectSwitch)
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
        toggleButton.text = if (isLegacyFireOs()) { if (up) "Disconnect" else "Connect" } else { getString(if (up) R.string.disconnect else R.string.connect) }
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

        val currentlyUp = try {
            WarpManager.isUp(this)
        } catch (t: Throwable) {
            setStatus("VPN backend error: ${shortError(t)}", StatusState.ERROR)
            return
        }

        if (currentlyUp) {
            // Remember the user's explicit choice, independently of the
            // Auto-connect switch.
            AppPreferences.setLastManualConnected(this, false)
            disconnect()
            return
        }

        AppPreferences.setLastManualConnected(this, true)
        setBusy(true)
        setStatus("Connecting…", StatusState.CONNECTING)

        try {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                // Fire OS 6 / API 25 is happier with the platform's original
                // activity-result path for the VPN consent dialog.
                if (isLegacyFireOs()) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, LEGACY_VPN_REQUEST)
                } else {
                    vpnPermission.launch(intent)
                }
            } else {
                doConnect()
            }
        } catch (t: Throwable) {
            setBusy(false)
            setStatus("VPN setup failed: ${shortError(t)}", StatusState.ERROR)
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
            } catch (t: Throwable) {
                setStatus("Failed: ${shortError(t)}", StatusState.ERROR)
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
            } catch (t: Throwable) {
                setStatus("Error: ${shortError(t)}", StatusState.ERROR)
            } finally {
                setBusy(false)
                refreshButtonOnly()
                if (isTvMode) focusToggleButton()
            }
        }
    }

    private fun refreshButtonOnly() {
        val up = WarpManager.isUp(this)
        toggleButton.text = if (isLegacyFireOs()) {
            if (up) "Disconnect" else "Connect"
        } else {
            getString(if (up) R.string.disconnect else R.string.connect)
        }
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
        val color = if (isLegacyFireOs()) {
            if (hasFocus) Color.rgb(255, 215, 64) else Color.rgb(255, 128, 24)
        } else {
            getColor(if (hasFocus) R.color.focus_yellow else R.color.warp_orange)
        }
        toggleButton.backgroundTintList = ColorStateList.valueOf(color)
        toggleButton.setTextColor(
            if (isLegacyFireOs()) {
                if (hasFocus) Color.rgb(18, 26, 35) else Color.WHITE
            } else {
                getColor(if (hasFocus) R.color.focus_text_dark else R.color.text_primary)
            }
        )
    }

    private fun setStatus(text: String, state: StatusState) {
        val dotColor = if (isLegacyFireOs()) {
            when (state) {
                StatusState.CONNECTED -> Color.rgb(46, 204, 113)
                StatusState.CONNECTING -> Color.rgb(255, 193, 7)
                StatusState.DISCONNECTED, StatusState.ERROR -> Color.rgb(255, 76, 76)
            }
        } else {
            when (state) {
                StatusState.CONNECTED -> getColor(R.color.status_green)
                StatusState.CONNECTING -> getColor(R.color.status_amber)
                StatusState.DISCONNECTED, StatusState.ERROR -> getColor(R.color.status_red)
            }
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

    private fun shortError(t: Throwable): String {
        val name = t::class.java.simpleName.ifBlank { "Throwable" }
        val msg = t.message?.replace('\n', ' ')?.take(120)
        return if (msg.isNullOrBlank()) name else "$name: $msg"
    }

    companion object {
        private const val LEGACY_VPN_REQUEST = 701
    }

    private enum class StatusState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
}
