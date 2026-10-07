# bt-picture (SnapLabel)

An Android app that takes a photo (camera or gallery), lets you draw on it /
add text and emoji stickers, and prints it on a Bluetooth LE label printer
(built and tested against a Rongta RPP30).

## Features

- CameraX capture, or pick from the gallery
- Photo editor: drag/pinch to move, scale and rotate text and emoji layers;
  freehand drawing; undo
- Print preview with a live Floyd–Steinberg dither simulation and
  brightness/contrast sliders, so what you see is what gets sent
- BLE printer scanning, pairing and connecting, with the connection
  remembered across launches
- In-app auto-update: checks this repo's
  [latest release](../../releases/latest) on launch and offers to download +
  install a newer build

## Printer protocol

The printer never sends anything back over BLE — there's no status or ack to
read. `app/.../printer/PrinterProtocol.kt` and `ImageDitherer.kt` encode what
was measured by hand against the real hardware: CPCL commands, an ~8
dots/mm (≈203dpi) printable area of about 576×320 dots, and a brightness/
contrast nudge that compensates the printer's thermal dot gain.

## Building

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Requires the Android
SDK (set `sdk.dir` in `local.properties`, which is gitignored).

## Releasing an update

The in-app update checker expects each GitHub release to be **tagged
`v<versionCode>`** (e.g. `v1`, `v2`, ...) matching `versionCode` in
`app/build.gradle.kts` — not semver. Bump `versionCode`/`versionName`, build,
then:

```bash
gh release create v<versionCode> app/build/outputs/apk/debug/app-debug.apk \
  --title "v<versionCode>" --notes "What changed"
```

The release APK must be signed with the same (debug) keystore as the
previously installed build, or Android will refuse to update in place.
