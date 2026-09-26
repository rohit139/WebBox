# WebBox

**WebBox** is a generic, configurable Android TV / Google TV WebView wrapper.

It loads any HTTP/HTTPS website fullscreen and is designed for use with a TV remote (D-pad). The same APK can be reused for different sites by changing the URL in Settings — no rebuild required.

Default website: `https://cinezo.org/`

## Features

- Fullscreen WebView on Android TV / Google TV
- Configurable website URL (persisted locally)
- Native TV-friendly Settings screen
- D-pad / remote navigation with optional focus enhancement
- Back navigates WebView history; long-press Back opens Settings
- External link blocking (configurable)
- Popup blocking (configurable)
- HTML5 video fullscreen support
- Loading and error screens with Retry / Settings
- Diagnostics (WebView version, device, network, last error)
- Clear website data (cookies, cache, storage)
- Reset to defaults

## Requirements

- Android TV, Google TV, or Android TV box (API 21+)
- Android SDK (for building)
- JDK 17+ (Android Studio JBR works)

## Build

### 1. Install Android SDK

Install [Android Studio](https://developer.android.com/studio) or command-line tools. Ensure platforms and build-tools are installed (this project targets **compileSdk 34**).

### 2. Configure environment

Set `ANDROID_HOME` (or create `local.properties` in the project root):

```properties
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
```

### 3. Build debug APK

**Windows (PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

**Linux / macOS:**

```bash
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

### 4. Locate APK

```
app/build/outputs/apk/debug/app-debug.apk
```

Release:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release-unsigned.apk
```

### 5. Unit tests

```bash
./gradlew test
```

## Install on Android TV

1. Enable **Unknown sources** / **Install unknown apps** for your file manager or ADB on the TV.
2. Copy the APK to the device (USB, network share, or ADB):

```bash
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

3. Open **WebBox** from the TV launcher (Leanback / Apps row).

## First launch

1. App starts and loads `https://cinezo.org/` fullscreen.
2. Use the D-pad to navigate the site.
3. **Long-press BACK** to open Settings (or press **Menu** if available).

## Settings

| Control | Description |
|--------|-------------|
| **Website URL** | HTTP/HTTPS URL to load. Scheme optional (`example.com` → `https://example.com`). |
| **Test Website** | Probes reachability (401/403 still count as reachable). |
| **Fullscreen** | Hide system bars. |
| **Landscape** | Lock landscape orientation. |
| **TV Navigation** | Enable D-pad focus enhancement + focus ring on the page. |
| **Block External Links** | Keep navigation on the configured site (media CDNs still allowed). |
| **Block Popups** | Prevent `window.open` / new windows from escaping the app. |
| **Remember Last Page** | Resume last visited page on next launch. |
| **Save & Load Website** | Validate, persist, and reload. |
| **Reload Website** | Reload current site. |
| **Clear Website Data** | Cookies, cache, local storage (with confirmation). |
| **Diagnostics** | URL, WebView/Android version, device, network, last error. |
| **Reset to Default** | Restore default URL and options. |
| **Cancel** | Close Settings without saving current edits. |

Settings are stored in app **SharedPreferences** and survive restarts.

## Remote / D-pad controls

| Key | Action |
|-----|--------|
| **D-pad ↑ ↓ ← →** | Move focus (native site focus, or enhanced spatial focus when TV Navigation is ON) |
| **OK / Center** | Activate focused element |
| **Back** | Exit video fullscreen → WebView history back → press again to exit app |
| **Long-press Back** | Open Settings |
| **Menu** | Open Settings (when the remote sends Menu) |

## Project structure

```
WebBox/
├── app/
│   ├── src/main/
│   │   ├── java/com/webbox/tv/
│   │   │   ├── WebBoxApp.kt
│   │   │   ├── data/          # AppSettings, SettingsRepository
│   │   │   ├── ui/            # MainActivity, SettingsActivity, DiagnosticsActivity
│   │   │   ├── util/          # UrlValidator, NetworkUtils
│   │   │   └── webview/       # WebViewController, NavigationHandler, TvInputHandler
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Security notes

- Only `http` / `https` URLs are accepted.
- SSL errors are **not** bypassed.
- File access and JS bridges are disabled by default.
- External apps are not launched for navigation by default.
- The app does not bypass DRM, paywalls, or authentication.

## Known limitations

- D-pad enhancement depends on site DOM structure; some custom players need site-side focus support.
- DRM / Widevine-protected streams depend on device WebView and site implementation.
- Physical Android TV device testing is required for full remote UX validation.
- Release APK is unsigned unless you configure a signing key.
- Soft keyboard for URL editing varies by TV OEM.

## License

Personal / sideload use. Not a commercial product.
