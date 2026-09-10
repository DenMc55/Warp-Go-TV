# Warp Go TV

A TV-first Cloudflare WARP (WireGuard) client for Android/Fire TV that can also provide a touch-friendly mobile interface from the same APK.

## v1.5

- Remembers the user's most recent manual **Connect / Disconnect** choice.
- **Auto-connect ON** always requests WARP ON after boot.
- **Auto-connect OFF** restores the last manual state.
- Boot delay reduced from **40 seconds to 20 seconds**.
- Startup attempts are gated by Android network availability, so a slow Wi-Fi/Ethernet/mobile startup does not waste retries.
- If background startup is blocked, opening the app provides another restore opportunity (without automatically showing the VPN permission dialog).
- Keeps the one-time **TV / Mobile** chooser, TV-first D-pad UI, mobile touch UI, logo/branding and permanent release signing introduced in v1.4.

The installed launcher name remains **Warp Go TV**. Inside the app, TV mode is titled **Warp Go TV** and Mobile mode is titled **Warp Go**.

To show the interface chooser again, clear the app's data or reinstall it.
