# Road's Eye for Android

Road's Eye turns the Android phone on your dash into a dashcam. It records continuously, clips the last stretch of driving on command, overlays speed and time, and can detect crashes and save footage automatically. No account and no subscription are required.

The Google Play listing and roadseye.com close on **October 1, 2026**. This repository is the full source for the final release (3.2.0), published so anyone can build it, learn from it, or keep it going.

> **Project status:** Road's Eye is discontinued. From now on there will most likely be **no more updates**, bug fixes or support. Any future news will be posted in this repository, so check here for more info.

> **Heads up:** some parts of the original app were removed to prepare the code for open source. Some bugs or errors may have been introduced along the way and might not have been caught. If something doesn't work as expected, that could be why, so please test your own builds carefully.

The iOS version lives at [RoadsEye/RoadsEye-iOS](https://github.com/RoadsEye/RoadsEye-iOS).

---

## Features

- **Continuous recording** in segments, with automatic cleanup of old footage
- **Clip it**: save the last stretch of driving with a tap (or a voice command)
- **Crash detection** using motion and speed data, with automatic saving
- **Speed and time overlay** on recorded video (mph or km/h)
- **Voice commands**: "Hey Road's Eye" followed by "start recording", "stop recording" or "clip it"
- **Auto-record** when driving starts
- **Picture-in-picture** so recording keeps going while you use another app
- **Home screen widgets**: Quick Record and lifetime driving stats
- Frame rate (15, 30 or 60 fps), resolution and zoom settings
- Saves to `Movies/RoadsEye` on the device, visible in any gallery or file manager
- Light, dark, and system appearance

---

## Requirements

| | |
|---|---|
| **Computer** | macOS, Windows or Linux that runs a current Android Studio |
| **Android Studio** | A recent stable release (the project uses Android Gradle Plugin 9.2 and Kotlin 2.3) |
| **JDK** | 21. Android Studio's bundled JDK works; Gradle can also download one automatically |
| **Android** | 12 (API 31) or newer |
| **Phone** | Recommended. Road's Eye is a dashcam app, so it needs a real phone's camera, GPS and motion sensors. The emulator is fine for working on the interface. |

There are no accounts, API keys or config files to set up: every dependency is downloaded by Gradle on the first build.

---

## Getting started

### 1. Get the code

```bash
git clone https://github.com/RoadsEye/RoadsEye-Android.git
```

Or, in Android Studio: **File → New → Project from Version Control…**, paste `https://github.com/RoadsEye/RoadsEye-Android.git`, and choose a folder.

### 2. Open it in Android Studio

1. **File → Open…** and select the `RoadsEye-Android` folder (the one containing `settings.gradle.kts`).
2. Wait for the **Gradle sync** to finish (the progress bar at the bottom). The first sync downloads the Android Gradle Plugin and all libraries, so it can take a few minutes.
3. If Android Studio asks to install a missing SDK platform or build tools, accept. The app compiles against Android SDK 37.

Android Studio creates `local.properties` (the path to your Android SDK) automatically. It's ignored by git, so it never ends up in a commit.

### 3. Run it on your phone

1. On the phone, turn on **Developer options**: **Settings → About phone**, then tap **Build number** seven times.
2. In **Settings → System → Developer options**, turn on **USB debugging** (or **Wireless debugging**).
3. Plug the phone in and tap **Allow** when asked to allow USB debugging from this computer.
4. In Android Studio, pick your phone from the device menu in the toolbar and press **Run** (▶︎ or **⌃R** on macOS, **Shift+F10** on Windows/Linux).

The first launch walks through onboarding and the Terms of Service, then asks for camera, location, microphone and notification permissions.

> **Already have Road's Eye from Google Play?** Builds from this repository use the placeholder application ID `com.example.roadseye`, so they install **next to** the Play Store version instead of replacing it. Your existing app and its settings are left alone.

### 4. The emulator (interface only)

The emulator can't really drive: it has no real motion or GPS data, and its camera is simulated. It's still handy for working on the interface. Create a device in **Tools → Device Manager** (any recent Pixel image works) and run the app on it to try onboarding, settings and the legal screens.

---

## Everyday Android Studio tips

| Action | macOS | Windows / Linux |
|---|---|---|
| Run the app | **⌃R** | **Shift+F10** |
| Stop the running app | **⌘F2** | **Ctrl+F2** |
| Build only (check for errors) | **⌘F9** | **Ctrl+F9** |
| Sync Gradle after editing a build file | **File → Sync Project with Gradle Files** | same |
| Open a file by name | **⇧⌘O** | **Ctrl+Shift+N** |
| Search the whole project | **⇧⌘F** | **Ctrl+Shift+F** |
| Show Logcat | **View → Tool Windows → Logcat** | same |

- **Log output** (`Log.d(...)` messages) appears in **Logcat** while the app runs.
- **Compose previews:** open a file with a `@Preview` function and switch the editor to **Split** or **Design** to see it.
- **Odd build errors?** **Build → Clean Project**, then **File → Invalidate Caches… → Invalidate and Restart** fixes most of them.

### Building from the command line

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected phone with:

```bash
./gradlew installDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`.

### Making a release build

Release builds need your own signing key; none is included. In Android Studio use **Build → Generate Signed App Bundle or APK…** and create a new key store when asked. Keep the key store and its passwords out of the repository (`*.jks` and `*.keystore` are already ignored).

---

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── terms_of_service.txt / privacy_policy.txt   Legal text shown inside the app
├── java/com/roadseye/dashcam/
│   ├── MainActivity.kt              App entry point, launch flow
│   │                                (Onboarding → Terms → Main screen → announcement),
│   │                                main camera screen and picture-in-picture
│   ├── CameraManager.kt             CameraX setup, preview, zoom
│   ├── RecordingManager.kt          Segmented recording and cleanup of old footage
│   ├── RecordingForegroundService.kt Keeps recording alive in the background
│   ├── ClippingManager.kt           Builds clips and crash saves from recent segments
│   ├── CrashDetectionManager.kt     Motion and speed based crash detection
│   ├── LocationSpeedManager.kt      GPS speed and units
│   ├── AutoRecordManager.kt         Starts recording when driving is detected
│   ├── VoiceCommandsManager.kt      "Hey Road's Eye" voice commands
│   ├── DrivingStatsManager.kt       Lifetime stats shown in Settings and the widget
│   ├── NotificationManager.kt       Recording and save notifications
│   ├── OnboardingScreen.kt          First-launch tutorial
│   ├── OpenSourceNotice.kt          "Now open source" announcement
│   ├── LegalDocumentsScreen.kt      In-app reader for the Terms and Privacy Policy
│   ├── settingssection/             One file per section of the Settings screen
│   ├── widget/                      Quick Record and stats home screen widgets
│   └── ui/theme/                    Colors, typography, light and dark themes
└── res/                             Icons, layouts for the widgets, strings
gradle/libs.versions.toml            Dependency versions
```

---

## Permissions the app asks for

Road's Eye asks for permissions only after the Terms are accepted. They're declared in `app/src/main/AndroidManifest.xml`.

| Permission | Why |
|---|---|
| Camera | Recording video |
| Microphone | Recording audio and voice commands (optional) |
| Location | Speed overlay, auto-record and crash detection |
| Notifications | Showing that recording is running and letting you know when videos are saved |
| Foreground service (camera) | Continuing to record while the app is in the background |

Motion sensors used for crash detection don't need a permission on Android.

---

## Privacy

Road's Eye has no analytics, crash reporting, ads, accounts or servers. Recordings stay on the device in `Movies/RoadsEye`. The only Google library used is Play services location, for speed and driving detection.

---

## Contributing

The project is no longer maintained, so issues and pull requests may never be reviewed or answered. The best way to keep Road's Eye going is to fork it and build on it yourself.

1. Fork the repository and create a branch for your change.
2. Make sure it builds (`./gradlew assembleDebug`) and, if you touched recording, test it on a real phone.
3. Open a pull request describing what changed and why.

Please never commit signing keys, key store passwords, `local.properties`, `google-services.json`, or any API keys or config files.

---

## Disclaimer

Road's Eye and its source code are provided **"as is", without warranty of any kind**, express or implied, including fitness for a particular purpose. The entire risk of using, building, modifying or distributing the app or this code is yours. To the maximum extent permitted by law, the creators and contributors are **not liable** for any claim, damages or other liability, including lost or corrupted recordings, missed or false crash detections, accidents, injury, or any use of recordings as evidence.

Road's Eye is **not a safety or emergency device**. Never interact with it while driving, and never rely on it to detect a crash or contact help.

Builds made or modified by anyone else are not reviewed, endorsed or supported by Road's Eye. The full terms are in [terms_of_service.txt](app/src/main/assets/terms_of_service.txt) and [privacy_policy.txt](app/src/main/assets/privacy_policy.txt), which are also shown inside the app.

---

## License

The source code is released under the [MIT License](LICENSE). You're free to use, copy, modify and distribute it, as long as the license notice is kept.

The MIT License covers the code only. The Road's Eye name and logo are not included (see Trademark below).

---

## Trademark

"Road's Eye" and the Road's Eye logo are the names and branding of the original app. If you publish your own build on Google Play or anywhere else, please give it a different name, icon and application ID (`applicationId` in `app/build.gradle.kts`).

---

## Acknowledgements

The move to open source was done with help from [Claude](https://claude.com/claude-code), Anthropic's AI assistant. Claude helped remove sensitive information and the ads, analytics and subscription code, write this README, and update the Terms of Service and Privacy Policy for the open-source release.
