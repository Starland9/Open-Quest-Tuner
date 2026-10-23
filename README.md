# Open Quest Tuner

Native Android toolkit for Meta Quest, built with Kotlin and Jetpack Compose.

Version: **0.19.2-NativeAutoTune-ForegroundFix**.

## Features

- Responsive dashboard and game library.
- Per-game profiles and manual tuning.
- Embedded wireless ADB pairing and connection.
- Headset tools and Wide Motion Mode controls.
- Per-game performance observations and measured automatic tuning.
- Experimental native immersive tuning HUD.

Auto Tune uses directly available headset telemetry. Missing required counters cannot certify a profile. Automatic resolution testing is unavailable without directly observed game render dimensions. The current native tuning path does not require OVR Metrics Tool.

The immersive HUD and latest foreground-detection repair require physical headset verification. Build and unit-test success alone do not establish headset compatibility or a performance improvement.

## Build

See [BUILDING.md](BUILDING.md). A full JDK 17, Android SDK 35 and Android NDK 27.2.12479018 are required.

## Licence

See [LICENSE](LICENSE). Third-party native header licence notices are retained with their source files.
