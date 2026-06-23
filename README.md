# WarpGo

A tiny one-button Android app that tunnels your phone's traffic through
**Cloudflare WARP** (WireGuard), useful for getting around networks that
interfere with traffic (e.g. campus Wi-Fi doing SNI-based blocking).

It does the same thing as Cloudflare's official **1.1.1.1** app, but as a
single self-contained APK with an in-app **port switcher** so you can dodge
networks that block WARP's default UDP port.

## How it works

- On first **Connect**, the app generates a WireGuard keypair **on the device**
  and registers a free WARP account with Cloudflare's API.
- **No private keys are stored in this repository** — each install registers its
  own account at runtime.
- It then brings up a real WireGuard tunnel (embedded `wireguard-android`
  engine) to Cloudflare.

## Ports

If WARP connects but no data flows (or won't connect), the network is likely
blocking WireGuard's UDP. Disconnect, pick another port, and Connect again:

| Port | Notes |
|------|-------|
| 2408 | WARP default |
| 4500 | IPsec NAT-T port — usually open |
| 500  | IKE port — usually open |

## Install

Grab the latest `WarpGo-*.apk` from the
[Releases](../../releases) page, copy it to your phone, and install it
(allow "install from unknown sources" if prompted). The first time you connect,
Android asks for VPN permission — approve it.

## Build

APKs are built automatically by GitHub Actions on every push to `main`
(`.github/workflows/build.yml`) and attached to a Release.

To build locally:

```
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Limitations

- If a network blocks WireGuard UDP on **all** ports, no WireGuard-based app can
  get through. Cloudflare's own 1.1.1.1 app has an extra HTTPS-disguised
  "MASQUE" fallback that this app does not implement.
