# Building Open Quest Tuner

## Requirements

- Full JDK 17, including javac.
- Android SDK platform 35.
- Android build-tools 35.0.0.
- Android NDK 27.2.12479018.
- Internet access for initial dependency downloads.

The wrapper selects Gradle 8.13. The project uses Android Gradle Plugin 8.13.2 and Kotlin 2.2.20. Set Android Studio's Gradle JDK to 17. Do not use JDK 25 with this wrapper.

Use Android Studio's SDK Manager to install the SDK and pinned NDK. Let Android Studio create local.properties, or set ANDROID_HOME and JAVA_HOME locally.

## Commands

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=2
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=2
```

The native HUD library is compiled during preBuild. Output APK: app/build/outputs/apk/debug/app-debug.apk.

## Signing

No signing keys are distributed in this source archive.

If signing/oqt-debug.keystore exists locally, the build uses that development key with the existing Android debug credentials. Otherwise Android uses the standard local debug keystore. Keep signing files outside version control.

To update an existing installation without a signing conflict, use the same original signing key. A newly generated debug key will not match an APK signed with a different key.

The release build currently shares development signing configuration. Configure a separate private release signing setup before public release distribution.

## Source package

The archive contains application source, unit tests, Gradle wrapper/build configuration and licence files. Generated output, captured device logs, build reports, local SDK paths, private keys and development-workflow folders are excluded.
