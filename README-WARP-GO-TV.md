# Warp Go TV

TV-focused modification of the open-source WarpGo Android app.

## Changes in this build

- App/launcher name changed to **Warp Go TV**.
- Separate application ID (`com.iknalos.warpgo.tv`) so it can coexist with the original WarpGo.
- Dark blue TV background with high-contrast text.
- Strong yellow focus outline for Fire TV / Android TV D-pad navigation.
- Large status display with green connected dot and red disconnected/error dot.
- Larger Connect / Disconnect button.
- Port rows are fully focusable and retain touch support.
- Selected port is remembered.
- **Auto-connect on boot** switch added.
- Boot reconnect uses the last selected port and only runs after VPN permission has previously been granted.
- Android TV launcher category added while keeping normal Android launcher support.

## Auto-connect note

Android requires the user to grant VPN permission interactively at least once. Connect manually once, then enable **Auto-connect on boot**. On reboot, Warp Go TV waits briefly for networking and attempts to restore the WARP tunnel.

## Build

The original repository uses Gradle 8.9 and JDK 17. In Android Studio, open the project folder and build the debug APK, or from a configured command line run:

```bash
gradle assembleDebug
```

Expected APK path:

`app/build/outputs/apk/debug/app-debug.apk`
