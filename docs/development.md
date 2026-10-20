# Development

CleanFrame TV has two halves: the web interface in `app/`, inherited from CleanFrame, and a small Kotlin launcher in `android/` that hosts it in a WebView. Most visual work happens in the browser preview; Android work needs a TV or an Android TV emulator.

## Browser preview

Start with Node.js 22+ and Python 3.10+, then `npm ci`. Install Chromium once with `npx playwright install chromium` for the browser checks (Linux hosts may need `npx playwright install --with-deps chromium`).

`npm run dev` starts http://localhost:4795/home/, bound to this computer only. `/home/` serves `app/`, the same files the APK bundles. Arrow keys, Enter and Escape mirror the remote; hold Enter on an app to try hold-to-close. Without the Android bridge, `device.js` serves a fixed mock catalogue (apps and two HDMI inputs) and two mock Bing photos, and device actions report that they need the TV. No success is simulated.

`npm run dev:public` serves the interface with only the bundled public fonts. Optional private fonts from upstream's tooling (`scripts/font_assets.py`, ignored `.local/`) are only ever used by the preview; they are never part of `app/` or the APK.

All UI assets, public fonts and the fifteen wallpapers are bundled. Glass materials and blurred panel backgrounds of bundled wallpapers are rendered ahead of time; see [assets](assets.md).

## Interface runtime

- `app/index.html` loads the device adapter, gallery/font/palette catalogues, `assets/icons/metrics.js` and `main.js`, with `style.css`.
- `main.js` owns the List and Dock layouts, panel stack, focus, preferences, wallpaper timers, Photo mode and hold-to-close.
- `device.js` is the only code that talks to Android. It maps brands to packages (`BRANDS`: SmartTube before the official YouTube app, an HDMI input named like "Switch" for Nintendo Switch), reads the catalogue, launches and closes apps, opens TV settings or the previous launcher, lists Bing photos and hands over ADB configuration. Keep platform details there.
- The logical stage is 1920 × 1080 and `fit()` scales it to the window. On Android, `index.html` asks for a 1920 px wide viewport and the WebView uses wide viewport + overview mode, so the stage lays out 1:1 even on TVs whose density would otherwise give a 960 × 540 CSS viewport. Check layouts at 1920 × 1080 and 1280 × 720.
- Preferences live in the WebView's localStorage under `cleanframe.home.v1` (fallback read from `cleanframe.shore.v1`). Upstream's migrations are kept: legacy `list` becomes Dark / List / Type; `shore` and `grid` become Photo / Dock / Glass. Look keys are stored in `look`; `clock` and `source` were added by this port. Keep migration checks when changing the format.
- `window.ShoreQA.state()` and `.where()` expose state and focus for browser tests. `minuteMs` shortens rotation intervals in an isolated browser test.

Keep runtime JavaScript ES5-compatible: Android TV devices ship old WebViews. The reference TV runs Android System WebView from Google Play, but other boxes may not update it. `node --check` checks syntax only; it does not enforce ES5.

## Android launcher

`android/app/src/main/java/io/github/mdrzn/cleanframetv/`:

| File | Role |
| --- | --- |
| `HomeActivity.kt` | The launcher (`singleTask`, HOME + LEANBACK_LAUNCHER). One full-screen WebView, kept alive while apps run. Back/Escape go to the interface (`CleanFrameBack`); Home while already home closes panels and Photo mode (`CleanFrameHome`). A launch that isn't a HOME intent while CleanFrame TV is the default home is forwarded to the home instance, so there is never a second WebView. Recreates the WebView if its renderer dies. Takes `--es config` and `--ez debug`. |
| `Bridge.kt` | `window.CleanFrameAndroid`: `appsJson`, `launch`, `closeApp`, `openSettings`, `openPreviousLauncher`, `is24Hour`, `photosJson`, `refreshPhotos`, `takeConfig`. All synchronous; actions return `""` or a short message for the interface. |
| `Catalog.kt` | Pure rules, unit tested: one entry per package (TV activity preferred), system apps without a TV entry hidden, HDMI inputs only (no tuners, composite, CEC children), input ids `input.<hwN>`, JSON, config splitting, button maps. |
| `AndroidCatalog.kt` | PackageManager and TvInputManager access, icon export (128 px PNG in `filesDir/icons`), launching (leanback intent, or the passthrough channel URI for inputs), closing (`killBackgroundProcesses`), input names and button targets in SharedPreferences. |
| `BingFeed.kt`, `PhotoMaker.kt`, `PhotoMeta.kt`, `Photos.kt` | Bing photo of the day: feed parsing, downloads, the material images the interface expects and the light/dark ink decisions, stored in `filesDir/photos` with a `photos.json` index. |
| `KeyRemapService.kt` | Optional accessibility service for remote buttons (key filtering only). |

The WebView loads `https://appassets.androidplatform.net/assets/index.html` through `WebViewAssetLoader`: `/assets/` is `app/` (bundled with `assets.srcDirs("../../app")`), `/photos/` and `/icons/` come from the app's private storage. Navigation to any other host is blocked. Native events reach the page as `cleanframe:apps-changed` (package installed, removed or updated) and `cleanframe:photos-updated`.

Permissions: `INTERNET` (Bing photos only), `KILL_BACKGROUND_PROCESSES` (hold-to-close) and a `<queries>` block for launcher intents. No `QUERY_ALL_PACKAGES`.

### Bing photos

The feed is `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=<language-COUNTRY>` and each image `https://www.bing.com<urlbase>_1920x1080.jpg`. Android TV draws its interface at 1080p on nearly all devices (video uses a separate plane), so larger images would only cost memory. Each new photo is processed once on a background thread: cover crop, background, thumbnail, field blur, frost, soft study and clear materials, the small panel image, and tone/ink values computed from luminance under the text areas. Photos are refreshed at most every 12 hours and the newest eight are kept. Failures keep the previous photos; with none, the built-in wallpapers are used.

## Build

Requirements: JDK 21, Android SDK platform 36 and build tools 36.1.0 (set `ANDROID_HOME` or `android/local.properties`). The Gradle wrapper is pinned by checksum.

```sh
npm run android:build                      # release APK
cd android && ./gradlew testReleaseUnitTest  # Kotlin unit tests
```

### Signing

Without a key, release builds are signed with the debug key. To sign with your own key, create `~/.config/cleanframe-tv/keystore.properties` (outside the repository, `chmod 600`):

```properties
storeFile=/home/you/.config/cleanframe-tv/release.jks
storePassword=...
keyAlias=cleanframe-tv
keyPassword=...
```

Keep a backup of the keystore: Android only installs an update signed with the same key. Never commit keys, passwords, device addresses or APKs.

## Working on a TV

```sh
npm run android:build && adb install -r android/app/build/outputs/apk/release/app-release.apk && adb shell input keyevent KEYCODE_HOME
adb logcat -s CleanFrameTV:I CleanFrameJS:I
```

- Turn on remote debugging once with `adb shell am start -n io.github.mdrzn.cleanframetv/.HomeActivity --ez debug true`; the page then appears in `chrome://inspect`. Scripts can attach with `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof io.github.mdrzn.cleanframetv)` and the DevTools protocol.
- Screenshots: `adb shell screencap -p /sdcard/cf.png && adb pull /sdcard/cf.png && adb shell rm /sdcard/cf.png`. Some vendors print text before the image on `adb exec-out screencap`, which corrupts the file.
- `adb shell input keyevent` drives the interface, but it bypasses accessibility services: remote-button assignments can only be tested with the physical remote.
- `am start -n …/.HomeActivity` (for configuration) is delivered to the running home instance.
- `adb shell dumpsys meminfo io.github.mdrzn.cleanframetv` reports memory. The WebView renderer runs inside the app process on low-RAM devices, so the total includes it.
