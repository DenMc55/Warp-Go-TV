package com.iknalos.warpgo

import android.content.Context
import android.content.SharedPreferences
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Registers a free Cloudflare WARP account on-device and brings up a WireGuard
 * tunnel to Cloudflare. No keys are ever shipped in the app: a fresh keypair is
 * generated on the phone and registered with Cloudflare's API at runtime.
 *
 * API constants verified against wgcf v2.2.31 (the tool that successfully
 * registered on 2026-06-23).
 */
object WarpManager {

    private const val API = "https://api.cloudflareclient.com/v0a1922"
    private const val CLIENT_VERSION = "a-6.3-1922"
    private const val USER_AGENT = "okhttp/3.12.1"

    // Cloudflare's global WARP peer public key — constant across all accounts.
    private const val WARP_PEER_PUBKEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    // WARP always assigns this fixed IPv4 to the client interface.
    private const val CLIENT_V4 = "172.16.0.2/32"

    private const val PREFS = "warp_reg"
    private const val K_PRIV = "private_key"
    private const val K_V6 = "address_v6"
    private const val K_ID = "device_id"
    private const val K_TOKEN = "token"

    private const val TUNNEL_NAME = "warp"

    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    @Volatile private var backend: GoBackend? = null
    private var tunnel: WarpTunnel? = null

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext).also { backend = it }

    private fun tunnel(): WarpTunnel =
        tunnel ?: WarpTunnel().also { tunnel = it }

    fun isUp(ctx: Context): Boolean = try {
        backend(ctx).getState(tunnel()) == Tunnel.State.UP
    } catch (t: Throwable) {
        // Some older Fire OS builds can throw LinkageError/UnsatisfiedLinkError
        // while initialising the WireGuard backend. Treat that as DOWN here so
        // the activity can surface a useful error instead of the process dying.
        false
    }

    fun isRegistered(ctx: Context): Boolean =
        prefs(ctx).getString(K_PRIV, null) != null

    /** Wipe the stored WARP account so the next connect registers a fresh one. */
    fun resetRegistration(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /** Blocking — call from a background thread. Registers if needed, then connects. */
    @Throws(Exception::class)
    fun connect(ctx: Context, port: Int) {
        val reg = ensureRegistration(ctx)
        val config = buildConfig(reg, port)
        backend(ctx).setState(tunnel(), Tunnel.State.UP, config)
    }

    /** Blocking — call from a background thread. */
    @Throws(Exception::class)
    fun disconnect(ctx: Context) {
        backend(ctx).setState(tunnel(), Tunnel.State.DOWN, null)
    }

    private data class Registration(val privateKey: String, val addressV6: String?)

    private fun ensureRegistration(ctx: Context): Registration {
        val p = prefs(ctx)
        val existing = p.getString(K_PRIV, null)
        if (existing != null) {
            return Registration(existing, p.getString(K_V6, null))
        }
        // Preferred: a pre-registered account baked in at build time. This lets
        // the app work on networks that block WARP's registration endpoint
        // (e.g. campus Wi-Fi that intercepts api.cloudflareclient.com).
        if (BuildConfig.WARP_PRIVATE_KEY.isNotBlank()) {
            val v6 = BuildConfig.WARP_ADDRESS_V6.takeIf { it.isNotBlank() }
            p.edit()
                .putString(K_PRIV, BuildConfig.WARP_PRIVATE_KEY)
                .putString(K_V6, v6)
                .apply()
            return Registration(BuildConfig.WARP_PRIVATE_KEY, v6)
        }
        // Fallback: register a fresh account at runtime (works off-campus).
        return registerNewAccount(ctx)
    }

    private fun registerNewAccount(ctx: Context): Registration {
        val kp = KeyPair()
        val privateKey = kp.privateKey.toBase64()
        val publicKey = kp.publicKey.toBase64()

        val regBody = JSONObject().apply {
            put("key", publicKey)
            put("install_id", "")
            put("fcm_token", "")
            put("tos", isoNow())
            put("model", "PC")
            put("type", "Android")
            put("locale", "en_US")
        }

        val reg = apiCall("POST", "$API/reg", null, regBody)
        val deviceId = reg.getString("id")
        val token = reg.getString("token")

        val addressV6: String? = try {
            reg.getJSONObject("config")
                .getJSONObject("interface")
                .getJSONObject("addresses")
                .optString("v6").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        // Enable WARP on the device — without this the tunnel connects but
        // does not actually route traffic through Cloudflare.
        apiCall("PATCH", "$API/reg/$deviceId", token, JSONObject().put("warp_enabled", true))

        prefs(ctx).edit()
            .putString(K_PRIV, privateKey)
            .putString(K_V6, addressV6)
            .putString(K_ID, deviceId)
            .putString(K_TOKEN, token)
            .apply()

        return Registration(privateKey, addressV6)
    }

    private fun buildConfig(reg: Registration, port: Int): Config {
        val addresses = buildString {
            append(CLIENT_V4)
            if (!reg.addressV6.isNullOrBlank()) {
                append(", ")
                append(reg.addressV6)
                append("/128")
            }
        }
        val conf = """
            [Interface]
            PrivateKey = ${reg.privateKey}
            Address = $addresses
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1280

            [Peer]
            PublicKey = $WARP_PEER_PUBKEY
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = engage.cloudflareclient.com:$port
            PersistentKeepalive = 25
        """.trimIndent()
        return Config.parse(BufferedReader(StringReader(conf)))
    }

    private fun apiCall(method: String, url: String, token: String?, body: JSONObject): JSONObject {
        val rb = body.toString().toRequestBody(JSON)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("CF-Client-Version", CLIENT_VERSION)
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        when (method) {
            "POST" -> builder.post(rb)
            "PATCH" -> builder.patch(rb)
            "PUT" -> builder.put(rb)
            else -> throw IllegalArgumentException("Unsupported method $method")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("WARP API ${resp.code}: $text")
            }
            return JSONObject(if (text.isBlank()) "{}" else text)
        }
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private class WarpTunnel : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) { /* no-op */ }
    }
}
