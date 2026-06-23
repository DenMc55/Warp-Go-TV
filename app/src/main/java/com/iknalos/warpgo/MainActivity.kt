package com.iknalos.warpgo

import android.net.VpnService
import android.os.Bundle
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
                setStatus("VPN permission denied", connected = false)
                setBusy(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleButton.setOnClickListener { onToggle() }
        binding.resetButton.setOnClickListener {
            WarpManager.resetRegistration(this)
            setStatus("Registration cleared. Next connect makes a fresh WARP account.", connected = false)
        }
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val up = WarpManager.isUp(this)
        binding.toggleButton.text = if (up) "Disconnect" else "Connect"
        setStatus(if (up) "Connected ✓" else "Not connected", connected = up)
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
            setStatus("Connecting…", connected = false)
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
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WarpManager.connect(applicationContext, port) }
                setStatus("Connected ✓  (port $port)", connected = true)
            } catch (e: Exception) {
                setStatus("Failed: ${e.message}", connected = false)
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
                setStatus("Disconnected", connected = false)
            } catch (e: Exception) {
                setStatus("Error: ${e.message}", connected = false)
            } finally {
                setBusy(false)
                refreshButtonOnly()
            }
        }
    }

    private fun refreshButtonOnly() {
        binding.toggleButton.text = if (WarpManager.isUp(this)) "Disconnect" else "Connect"
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.toggleButton.isEnabled = !value
        binding.progress.visibility = if (value) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setStatus(text: String, connected: Boolean) {
        binding.statusText.text = text
        val color = if (connected) 0xFF2E7D32.toInt() else 0xFF757575.toInt()
        binding.statusText.setTextColor(color)
    }
}
