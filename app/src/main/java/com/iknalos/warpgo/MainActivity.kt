package com.iknalos.warpgo

import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iknalos.warpgo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var busy = false

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restorePreferences()

        binding.toggleButton.setOnClickListener { onToggle() }
        binding.portGroup.setOnCheckedChangeListener { _, _ ->
            AppPreferences.setSelectedPort(this, selectedPort())
        }
        binding.autoConnectSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setAutoConnectOnBoot(this, checked)
        }
        binding.resetButton.setOnClickListener {
            WarpManager.resetRegistration(this)
            setStatus("Registration cleared", StatusState.DISCONNECTED)
        }

        // TV-first: land on the one control most people need. post() waits
        // until Fire TV has completed the first layout/focus pass.
        binding.toggleButton.post {
            binding.toggleButton.requestFocus()
        }
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun restorePreferences() {
        when (AppPreferences.selectedPort(this)) {
            2408 -> binding.port2408.isChecked = true
            500 -> binding.port500.isChecked = true
            else -> binding.port4500.isChecked = true
        }
        binding.autoConnectSwitch.isChecked = AppPreferences.autoConnectOnBoot(this)
    }

    private fun refreshState() {
        val up = WarpManager.isUp(this)
        binding.toggleButton.text = getString(if (up) R.string.disconnect else R.string.connect)
        if (up) {
            setStatus("Connected  ·  port ${selectedPort()}", StatusState.CONNECTED)
        } else {
            setStatus("Disconnected", StatusState.DISCONNECTED)
        }
    }

    private fun selectedPort(): Int = when (binding.portGroup.checkedRadioButtonId) {
        binding.port4500.id -> 4500
        binding.port500.id -> 500
        else -> 2408
    }

    private fun onToggle() {
        if (busy) return
        if (WarpManager.isUp(this)) {
            disconnect()
        } else {
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
            }
        }
    }

    private fun refreshButtonOnly() {
        binding.toggleButton.text = getString(
            if (WarpManager.isUp(this)) R.string.disconnect else R.string.connect
        )
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.toggleButton.isEnabled = !value
        binding.progress.visibility = if (value) android.view.View.VISIBLE else android.view.View.GONE
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
        binding.statusText.text = display
        binding.statusText.setTextColor(Color.WHITE)
    }

    private enum class StatusState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
}
