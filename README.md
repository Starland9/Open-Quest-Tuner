# OpenQuestTuner

> 🇫🇷 **En bref** : OpenQuestTuner est une application libre (GPL-3.0) pour casques Meta Quest
> qui applique des réglages par jeu (fréquence d'affichage, résolution de rendu, niveaux CPU/GPU,
> rendu fovéal) grâce à un client ADB intégré. Après une première autorisation depuis un PC, tout
> se fait dans le casque. L'interface est disponible en français et en anglais. La documentation
> de conception (`specs/`) et le tableau de compatibilité sont rédigés en français.

OpenQuestTuner is a free and open-source Android app for Meta Quest headsets. It stores
performance settings per game and applies them when it launches the game:

- refresh rate;
- render resolution per eye;
- CPU and GPU levels;
- fixed and dynamic foveated rendering.

It does so through the headset's `debug.oculus.*` system properties, using an ADB client embedded
in the app. After a one-time authorization from a PC, everything happens inside the headset.

**Status**: first version (MVP). It is tested on a Meta Quest 3 (Horizon OS build 207). Other
headsets use values from Meta's documentation, and their settings are marked *Experimental*.

## ⚠️ Read this first

- **Undocumented properties.** `debug.oculus.*` properties are debugging tools, not a supported
  Meta feature. Any Horizon OS update can change them or make them stop working. Use the app at
  your own risk: it comes with no warranty (see the license).
- **Settings apply to the whole headset, not just one game.** Android system properties are
  global. Once applied, the settings stay active after the game closes, for every app, including
  games launched from the Quest menu, until you tap **Reset all** or restart the headset. A
  restart always clears them.
- **Heat and battery.** High CPU/GPU levels and resolutions above ×1.0 make the headset warmer
  and shorten battery life. When it gets hot, the headset lowers its own performance. The app
  shows the thermal state and warns you from the *moderate* level on.
- **Experimental badges.** A setting marked *Experimental* has not been verified yet on your
  headset model. Verified effects are listed in [docs/compatibility.md](docs/compatibility.md).

## Features

- **Per-game profiles**: six settings, each of which can stay on *Game default*.
- **Apply and launch** in one tap. The app stops the game, writes the 7 managed properties (a
  setting left on *Game default* is cleared, so nothing leaks from the previous game), then
  launches the game.
- **Reset all**: clears every managed property in one tap. Your saved profiles are kept.
- **Game list**: VR games only, with search, a *Profile* badge, and a quick *Launch* button.
- **Diagnostic**:
  - the properties active right now;
  - the headset's thermal state, which does not need a connection;
  - an *Active on the headset* / *Not applied* indicator on each profile.
- **Safe by design**:
  - the app can only write a closed list of 7 properties, with values picked from menus, so
    there is no free-form shell;
  - nothing it writes survives a restart;
  - the ADB key never leaves the app's private storage.

## Requirements

- A Meta Quest 3, 3S, 2 or Pro with **developer mode** enabled (Meta developer account).
- The headset on Wi-Fi.
- A computer with [ADB](https://developer.android.com/tools/adb) for the first authorization
  only.

## Install

There are no published releases yet: build the APK (see below), then sideload it:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

The app then appears in the headset's library, under *Unknown sources*.

## Connect the app to the headset

Horizon OS does not expose Android's "Wireless debugging" pairing screen inside the headset, so
the first authorization goes through a PC. After that, no PC is needed.

1. **Once, with a PC.** Plug the headset in and run:

   ```bash
   adb tcpip 5555
   ```

   Unplug the cable. In the app, open **Connection & tools** and tap **Connect (via PC)**. Then
   accept the debugging prompt in the headset, with *Always allow*.
2. **Switch to wireless.** Tap **Switch to wireless** and accept Horizon OS's "allow wireless
   debugging on this network" window. The key you authorized in step 1 is reused: no pairing
   code is needed.
3. **From then on**, the app reconnects by itself when it starts.
   - After a restart, Horizon OS turns wireless debugging off. On our Quest 3, port 5555 stayed
     open, so the app reconnected through it: just tap **Switch to wireless** again.
   - If port 5555 is closed too, repeat step 1.

If your headset does show Android's "Wireless debugging" screen, the pairing-code card is a
fallback that works without a PC.

## Build

You need JDK 17 or newer (tested with 21) and the Android SDK with platform 35.

```bash
./gradlew test assembleDebug     # JVM tests, then app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # optimized (R8) app/build/outputs/apk/release/app-release.apk
./gradlew lintDebug
```

The release APK is optimized with R8: about 9 MB instead of 37 MB, with a smoother UI. It is
signed with your local debug key until a publishing key exists. That way, it installs over a
debug build without losing the app's authorized ADB key or your profiles. Android refuses
unsigned APKs. Do not distribute an APK signed with a debug key.

The `core/` package is plain Kotlin with no Android imports: every rule (commands, profiles,
connection policy, thermal levels…) is covered by JVM tests that run without a headset.

## Documentation

- [docs/compatibility.md](docs/compatibility.md) lists what has been verified, on which
  headset, with which measurement tool (French).
- [specs/001-game-profiles-mvp/](specs/001-game-profiles-mvp/) contains the specification, plan,
  research, contracts and tasks of this version. It was written with
  [Spec Kit](https://github.com/github/spec-kit), in French.
- [.specify/memory/constitution.md](.specify/memory/constitution.md) holds the project's
  principles: headset safety first, clean-room and privacy, verified on a real headset,
  testable core, simplicity.

## Privacy

The app has no analytics and no account, and it sends nothing to the internet. Its only network
traffic is the local ADB connection to its own headset (127.0.0.1). While looking for the wireless
debugging port, it also sends a local mDNS query.

## Third-party libraries

| Library | License |
|---|---|
| [libadb-android](https://github.com/MuntashirAkon/libadb-android) | GPL-3.0-or-later OR Apache-2.0 (a few files also carry BSD-3-Clause or MIT notices) |
| [spake2-android](https://github.com/MuntashirAkon/spake2-java) | LGPL-3.0 |
| [Conscrypt](https://github.com/google/conscrypt) | Apache-2.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) (`bcpkix`) | Bouncy Castle Licence (MIT-style) |
| Jetpack Compose, AndroidX, Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |

## Not affiliated

OpenQuestTuner is an independent, clean-room project. It is not affiliated with, endorsed by or
derived from Quest Games Optimizer, and it is not affiliated with Meta. *Meta Quest* and
*Horizon OS* are trademarks of Meta Platforms, Inc.

## License

[GPL-3.0](LICENSE).
