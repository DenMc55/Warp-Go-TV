package com.iknalos.warpgo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var busy = false
    private var isTvMode = true

    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var toggleButton: TextView
    private lateinit var portGroup: RadioGroup
    private lateinit var port4500: RadioButton
    private lateinit var port2408: RadioButton
    private lateinit var port500: RadioButton
    private lateinit var autoConnectSwitch: CompoundButton
    private lateinit var resetButton: TextView

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
        // Fire OS 6 (Android 7.1/API 25) has a resource-inflater bug that can
        // crash while parsing the XML first-run screen. Build this one small
        // screen entirely in code so no XML view inflation is involved.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(7, 24, 46))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(24), dp(32), dp(28))
        }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.warp_go_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Warp Go TV"
        }
        column.addView(logo, linearParams(180, 180, top = 8))

        val title = setupText("Choose interface", 24f, true).apply {
            gravity = Gravity.CENTER
        }
        column.addView(title, wrapParams(top = 8))

        val help = setupText("Select the layout for this device.", 14f, false, secondary = true).apply {
            gravity = Gravity.CENTER
        }
        column.addView(help, wrapParams(top = 6))

        val modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        column.addView(
            modeGroup,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        )

        val modeTv = setupRadio("TV").apply {
            id = View.generateViewId()
            isChecked = true
        }
        val modeMobile = setupRadio("Mobile").apply {
            id = View.generateViewId()
        }
        modeGroup.addView(modeTv, radioParams())
        modeGroup.addView(modeMobile, radioParams(top = 8))

        val okButton = TextView(this).apply {
            text = "OK"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(247, 249, 252))
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            background = roundedBackground(Color.rgb(246, 130, 31), 32)
            setOnFocusChangeListener { view, focused ->
                (view as TextView).apply {
                    if (focused) {
                        background = roundedBackground(Color.rgb(255, 213, 74), 32)
                        setTextColor(Color.rgb(23, 32, 42))
                    } else {
                        background = roundedBackground(Color.rgb(246, 130, 31), 32)
                        setTextColor(Color.rgb(247, 249, 252))
                    }
                }
            }
        }
        column.addView(
            okButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply { topMargin = dp(24) }
        )

        setContentView(scroll)

        // Deliberately default to TV. On a television the mobile layout can look
        // usable while hiding TV-only controls below the fold; the reverse is obvious.
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

    private fun setupText(
        value: String,
        sizeSp: Float,
        bold: Boolean,
        secondary: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(
            if (secondary) Color.rgb(184, 195, 209) else Color.rgb(247, 249, 252)
        )
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun setupRadio(label: String): RadioButton = RadioButton(this).apply {
        text = label
        textSize = 20f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(247, 249, 252))
        setPadding(dp(16), 0, dp(16), 0)
        isFocusable = true
        buttonTintList = ColorStateList.valueOf(Color.rgb(246, 130, 31))
        setOnFocusChangeListener { view, focused ->
            view.background = if (focused) {
                roundedStrokeBackground(Color.TRANSPARENT, Color.rgb(255, 213, 74), 2, 10)
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
        }
    }

    private fun radioParams(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(64)
    ).apply { topMargin = dp(top) }

    private fun linearParams(widthDp: Int, heightDp: Int, top: Int = 0) =
        LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply { topMargin = dp(top) }

    private fun wrapParams(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun roundedBackground(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun roundedStrokeBackground(
        fill: Int,
        stroke: Int,
        strokeDp: Int,
        radiusDp: Int
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(strokeDp), stroke)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showSelectedInterface() {
        isTvMode = AppPreferences.uiMode(this) == AppPreferences.MODE_TV

        // Fire OS 6 / Android 7.1 (API 25) has a broken binary-XML string-pool
        // parser on some first-generation Fire TV Stick 4K firmware. The same
        // app works once XML inflation is avoided, so API 25 gets a fully
        // programmatic compatibility UI. Newer devices keep the normal XML UI.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
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
        val backgroundColor = Color.rgb(5, 27, 51)
        val primary = Color.rgb(247, 249, 252)
        val secondary = Color.rgb(184, 195, 209)
        val orange = Color.rgb(246, 130, 31)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (isTvMode) 36 else 24), dp(20), dp(if (isTvMode) 36 else 24), dp(28))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(TextView(this).apply {
            text = if (isTvMode) "Warp Go TV" else "Warp Go"
            textSize = if (isTvMode) 30f else 32f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, wrapParams(top = 4))

        column.addView(TextView(this).apply {
            text = "Cloudflare WARP tunnel"
            textSize = 15f
            setTextColor(secondary)
            gravity = Gravity.CENTER
        }, wrapParams(top = 4))

        statusText = TextView(this).apply {
            text = "●  Disconnected"
            textSize = if (isTvMode) 24f else 22f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        column.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(28) })

        progress = ProgressBar(this).apply { visibility = View.GONE }
        column.addView(progress, wrapParams(top = 6))

        toggleButton = TextView(this).apply {
            text = "Connect"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = isTvMode
            background = roundedBackground(if (isTvMode) Color.rgb(255, 213, 74) else orange, 34)
        }
        column.addView(toggleButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (isTvMode) 66 else 66)).apply { topMargin = dp(22) })

        if (isTvMode) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(30) })

            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(28) })
            row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            buildLegacyPortSection(left, primary, secondary)
            buildLegacyControlSection(right, primary, secondary, orange)
        } else {
            buildLegacyPortSection(column, primary, secondary, topMargin = 34)
            buildLegacyControlSection(column, primary, secondary, orange, topMargin = 20)
        }

        setContentView(scroll)
    }

    private fun buildLegacyPortSection(
        parent: LinearLayout,
        primary: Int,
        secondary: Int,
        topMargin: Int = 0
    ) {
        parent.addView(TextView(this).apply {
            text = "Connection port"
            textSize = 18f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { this.topMargin = dp(topMargin) })

        parent.addView(TextView(this).apply {
            text = if (isTvMode) "Change only if your network blocks WARP." else "If a network blocks WARP, try another port. 2408 is the default; 4500 and 500 are usually left open by firewalls."
            textSize = 13f
            setTextColor(secondary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        portGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        parent.addView(portGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        fun portRadio(label: String): RadioButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = label
            textSize = 17f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(primary)
            setPadding(dp(8), 0, dp(8), 0)
            isFocusable = isTvMode
            buttonTintList = ColorStateList.valueOf(Color.rgb(246, 130, 31))
        }

        port4500 = portRadio("4500 (recommended)")
        port2408 = portRadio("2408 (WARP default)")
        port500 = portRadio("500")
        portGroup.addView(port4500, radioParams())
        portGroup.addView(port2408, radioParams())
        portGroup.addView(port500, radioParams())
        port4500.isChecked = true
    }

    private fun buildLegacyControlSection(
        parent: LinearLayout,
        primary: Int,
        secondary: Int,
        orange: Int,
        topMargin: Int = 0
    ) {
        if (isTvMode) {
            parent.addView(TextView(this).apply {
                text = "Controls"
                textSize = 20f
                setTextColor(primary)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { this.topMargin = dp(topMargin) })
        }

        autoConnectSwitch = SwitchCompat(this).apply {
            text = "Auto-connect on boot"
            textSize = 17f
            setTextColor(primary)
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = isTvMode
            setPadding(dp(8), 0, dp(8), 0)
        }
        parent.addView(autoConnectSwitch as View, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(if (isTvMode) 8 else topMargin) })

        parent.addView(TextView(this).apply {
            text = "Requires one manual connection first."
            textSize = 12f
            setTextColor(secondary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })

        resetButton = TextView(this).apply {
            text = "Reset WARP account"
            textSize = 16f
            setTextColor(orange)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = isTvMode
        }
        parent.addView(resetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) })

        parent.addView(TextView(this).apply {
            text = "Port changes take effect on the next connection."
            textSize = 12f
            setTextColor(secondary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
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
        toggleButton.text = if (up) "Disconnect" else "Connect"
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
        toggleButton.text = if (WarpManager.isUp(this)) "Disconnect" else "Connect"
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
