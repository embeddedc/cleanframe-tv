# CleanFrame TV Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn the CleanFrame Tizen web app into an installable Android TV launcher (CleanFrame TV) and verify it on the reference Mi TV Q1.

**Architecture:** A minimal Kotlin app (`android/`) hosts CleanFrame's existing HTML/CSS/ES5 UI (`app/`) in a full-screen WebView served by `WebViewAssetLoader`. A synchronous `@JavascriptInterface` bridge (`window.CleanFrameAndroid`) replaces the Tizen calls in `app/device.js`. A small Bing feed downloader generates the same image set the UI uses for bundled wallpapers.

**Tech Stack:** ES5 JavaScript/HTML/CSS (TV runtime), Node 22 + Python 3 (checks), Kotlin + Android Gradle Plugin, `androidx.webkit`, JUnit (JVM tests), ADB for device tests.

Design: `docs/plans/2026-10-20-cleanframe-tv-design.md`.

---

## Reference facts (from reading the code)

- `app/main.js` (1882 lines, ES5): constants L1-60 (`PRIMARY_APPS` L15-26, `WALLS`/`ADAPTIVE_INK` L28-29, `UTIL` L51, `MSG.leave` L53); launch/discovery L459-490 (`FrameDevice.launchApp`, `FrameDevice.apps`); `utils()`/`utilAction` L494-504; prefs `load/clean/serialise/initialise/next/write` L317-382 (key `cleanframe.home.v1`); Settings rows/groups L817-918 (`samsung` group); clock `clockText` L1058; image paths `matSrc/fieldSrc/loadBundle` L1621-1634 and thumbnails L1405, L1432 (all built from `WP + id`).
- `safeIcon()` accepts paths starting with `/` → native icons can be served same-origin as `/icons/<pkg>.png`.
- `applyAdaptiveInk()` is inactive without a palette; bundled palettes are `locked`, so Bing photos need **no** ink palette — only catalogue metadata (`tone`, `ut.tr/br` = `light|mist`, `frostVeil`, `listColumn`, `dockQuiet`, `clockMist`).
- Bundled image set per wallpaper: `wallpapers/<id>.jpg` (1920 wide), `<id>-thumb.jpg` (512 wide), `<id>-panel.jpg` (320x180, from Frost), `materials/<id>-field-blur.jpg` (28px blur, sat .92), `-field-frost.jpg` (42px), `-field-clear.jpg` (2px, sat 1.06), `-study-soft.jpg` (lower scrim + 14px blur), all 1920x1080 centred crop.
- Reference TV: inputs `com.mediatek.tvinput/.hdmi.HDMIInputService/HW4|HW5|HW6` = HDMI 1|2|3; HDMI 2 = Nintendo Switch, HDMI 3 = PC. Passthrough launch verified: `ACTION_VIEW content://android.media.tv/passthrough/<urlencoded inputId>`.

---

### Task 1: Remove Tizen-only material and rename the project

**Files:** delete `app/config.xml`, `scripts/package.py`, `scripts/tv.py`, `scripts/native-home/`, `scripts/export-public.py`, `scripts/check-public.py`, `docs/agent-tv-setup-guide.md`, `docs/tv-status.md`, `docs/public-release.md`; modify `package.json`, `scripts/check.py`, `AGENTS.md`, `.gitignore`.

1. `git rm` the files above.
2. `package.json`: name `cleanframe-tv`, version `0.1.0`, Android TV description; scripts keep `dev`, `dev:public`, `check`, `check:browser`; add `android:build` (`cd android && ./gradlew assembleRelease`).
3. `scripts/check.py`: drop the `config.xml`/Tizen namespace assertions; keep asset/JS checks.
4. Run `npm run check` → passes. Commit `chore: drop Tizen packaging for the Android TV port`.

### Task 2: Android device adapter (`app/device.js`)

**Files:** rewrite `app/device.js`; create `scripts/test-device.mjs`; modify `package.json` (`check` runs it).

`window.FrameDevice` API (all callbacks `done(err, value)`; bridge is synchronous):
- `primaryApps()` → brand definitions present on the device: `[{id, name, mark:true, appId}]` in BRANDS order.
- `apps(done)` → `[{appId, name, brand, icon}]` for every launchable app and HDMI input (inputs: `appId = "input.<n>"` safe id, mapped natively).
- `launchApp(appId, done)`, `settings(done)`, `previousLauncher(done)`, `is24Hour()`, `photos()`, `refreshPhotos()`, `takeConfig()`.
- `BRANDS` (order = default Home order): primevideo `com.amazon.amazonvideo.livingroom`; youtube `org.smarttube.stable|org.smarttube.beta|com.teamsmart.videomanager.tv|com.google.android.youtube.tv`; jellyfin `org.jellyfin.androidtv|com.github.damontecres.wholphin`; twitch `com.s0und.s0undtv|tv.twitch.android.app`; netflix `com.netflix.ninja`; disneyplus `com.disney.disneyplus`; spotify `com.spotify.tv.android`; appletv `com.apple.atve.androidtv.appletv`; stremio `com.stremio.one`; tubi `com.tubitv`; hbomax `com.wbd.stream|com.hbo.hbonow`; nintendoswitch = input whose name matches `/switch/i`.
- Without a bridge (browser preview) return a deterministic mock list (8 apps + 2 inputs) so the preview works.
1. Write `scripts/test-device.mjs` (loads device.js in a `vm` context with a fake `window.CleanFrameAndroid`) asserting: brand matching picks the first installed package, inputs map to `input.*`, unknown apps keep native icon path, mock mode returns 10 entries. Run → FAIL.
2. Implement device.js (ES5). Run → PASS. Commit `feat: Android device adapter`.

### Task 3: Apps, launching and Back in `main.js`

**Files:** `app/main.js` (L15-26, L53, L459-470, key handling for Back on Home).
1. Replace the Tizen `PRIMARY_APPS` literal with `FrameDevice.primaryApps()` (fallback `[]`); keep brand names/marks for icons.
2. `launch()` uses `a.appId`; errors show the existing message panel ("Couldn't open …").
3. Remove `MSG.leave` / Samsung Home prompt; Back on Home does nothing.
4. Listen for `cleanframe:apps-changed` → `refreshCatalog()`.
5. `npm run check`; browser preview shows mock apps. Commit.

### Task 4: Photo mode replaces Art Mode

**Files:** `app/main.js` (UTIL, SVG.art, `utilAction`, key dispatch), `app/style.css`.
1. `UTIL.art` → `['Photo mode', 'photo']`, SVG id `photo` (reuse the picture glyph).
2. `utilAction('photo')` sets `S.photoMode = true`, adds stage class `is-photo-mode`; any key while active clears it and restores focus.
3. CSS: `.is-photo-mode` hides list, dock, controls and focus glass (opacity/transform only), keeps clock; shows `.photo-caption` when the wall has `caption`.
4. Browser preview: enter/exit with keys; `npm run check`. Commit.

### Task 5: Settings — Android TV group, 24-hour clock, photo source

**Files:** `app/main.js` (LOOK_KEYS/LOOK_VALUES/VALUE_NAMES, `clean()`, settings rows/detail sections/detailOk, `clockText`), `app/style.css` if needed.
1. LOOK: add `clock: ['24','12']` (default from `FrameDevice.is24Hour()`), `source: ['builtin','bing']` (default `builtin`). Names: "24-hour", "12-hour", "Built-in", "Bing photo of the day".
2. Layout group: add "Clock" horizontal section. Background group: add "Source" section above Wallpaper.
3. `clockText()` honours `S.c.clock`.
4. `samsung` group → `android` ("Android TV", value "TV settings"): items "TV settings" (`FrameDevice.settings`), "Previous launcher" (`FrameDevice.previousLauncher`).
5. Migration: old saves without new fields get defaults (`pick`). `npm run check`. Commit.

### Task 6: Wallpaper sources and per-photo base paths

**Files:** `app/main.js` (WALLS init, `wall()`, path builders L1405/L1432/L1621-1634, rotation/picker filtering), `app/index.html` unchanged.
1. Each wall gets `base` (default `'assets/'`); helper `wallSrc(id, kind)` builds `base + 'wallpapers/'|'materials/' + …`; replace the five hard-coded builders.
2. At start, append `FrameDevice.photos()` entries (`source:'bing'`, `base:'/photos/'`, `caption`); `visibleWalls()` filters by `S.c.source` and falls back to built-in when empty (status note "Bing photos are still downloading").
3. Rotation and the wallpaper picker iterate `visibleWalls()`.
4. On `cleanframe:photos-updated` re-read `FrameDevice.photos()`; if the source is Bing and the current wall vanished, switch to the newest.
5. Browser: mock adapter returns two fake Bing entries pointing at bundled files. `npm run check`. Commit.

### Task 7: Brand icons

**Files:** `app/assets/icons/{jellyfin,twitch,nintendoswitch}.svg`, `app/assets/icons/SIMPLE-ICONS-LICENSE.txt` (exists), `app/assets/icons/SOURCES.json`, `app/assets/icons/metrics.js`.
1. Add Simple Icons (CC0) glyphs; record sources.
2. Regenerate metrics with `node scripts/build-icon-metrics.mjs` (or compute bounds the same way) and copy the three entries into `metrics.js`.
3. Browser preview shows marks. Commit.

### Task 8: Android Gradle project skeleton

**Files:** `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/app/build.gradle.kts`, Gradle wrapper, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/` (icon, banner 320x180, theme), `.gitignore`.
1. Download the official Gradle distribution, verify its published SHA-256, generate the wrapper.
2. App module: `applicationId = "io.github.mdrzn.cleanframetv"`, minSdk 26, target/compile 36, Kotlin, `androidx.webkit:webkit`; `assets.srcDirs("../../app")`; `abiFilters` not needed (no native code). Signing config reads `~/.config/cleanframe-tv/keystore.properties` (outside repo).
3. Manifest: `HomeActivity` (`singleTask`, `stateNotNeeded`, `screenOrientation=landscape`, HOME+DEFAULT+MAIN+LEANBACK_LAUNCHER), `android:banner`, `uses-feature leanback required=false`, `touchscreen required=false`, INTERNET, `<queries>` for LEANBACK_LAUNCHER/LAUNCHER.
4. Create the release keystore at `~/.config/cleanframe-tv/release.jks` (+ properties, chmod 600).
5. `./gradlew assembleRelease` builds an empty activity. Commit.

### Task 9: HomeActivity + WebView + bridge

**Files:** `android/app/src/main/java/io/github/mdrzn/cleanframetv/{HomeActivity,Bridge,AppCatalog,Inputs}.kt`, tests under `src/test/`.
1. Tests first (JVM): `InputsTest` (HDMI-only filter, `input.<n>` ids, custom names), `AppCatalogTest` (self excluded, de-dup, LEANBACK preferred).
2. WebView: JS + DOM storage, `WebViewAssetLoader` (`/assets/` → assets, `/photos/` → `filesDir/photos`, `/icons/` → `filesDir/icons`), load `https://appassets.androidplatform.net/assets/index.html`, black background, no scrollbars, focus to WebView.
3. Keys: `KEYCODE_BACK` → JS keydown 27 (never finish); `onNewIntent(HOME)` → JS `window.CleanFrameHome && CleanFrameHome()` (closes panels/photo mode).
4. Bridge (`@JavascriptInterface`): `appsJson`, `launch(id)` → error string or "", `openSettings`, `openPreviousLauncher` (Projectivy if installed, else system home chooser), `is24Hour`, `photosJson`, `refreshPhotos`, `takeConfig` (JSON from `--es config`, cleared after read).
5. Package add/remove receiver → JS event `cleanframe:apps-changed`; `onRenderProcessGone` → recreate WebView.
6. Native icons: render app icon/banner to `filesDir/icons/<pkg>.png` once.
7. `./gradlew testReleaseUnitTest assembleRelease`. Commit.

### Task 10: Bing photo feed

**Files:** `android/app/src/main/java/io/github/mdrzn/cleanframetv/{BingFeed,PhotoMaker}.kt`, tests.
1. Tests: `BingFeedTest` parses a captured JSON sample (id from `startdate`, URL `https://www.bing.com<urlbase>_1920x1080.jpg`, caption from `copyright`); `PhotoMakerTest` tone/ut decisions on synthetic bright/dark bitmaps (pure functions on luminance arrays).
2. Downloader: at most every 12 h on resume, background thread, keep last 8, write `filesDir/photos/{wallpapers,materials}/bing-<date>*.jpg` + `photos.json`; events `cleanframe:photos-updated`.
3. PhotoMaker: centre-crop 1920x1080; blur via downscale ×1/4 + box-blur passes + upscale (28/42/14/2 px equivalents), saturation tweaks, Soft lower scrim; thumb 512x288; panel 320x180 from Frost; metadata from region luminance (tl clock, tr/br controls, list column).
4. Tests pass; build. Commit.

### Task 11: Device test on the Mi TV

1. `adb install -r app-release.apk`; `cmd package set-home-activity io.github.mdrzn.cleanframetv/.HomeActivity`; press HOME.
2. Push config: Home = Prime Video, YouTube, Jellyfin, Twitch, RaiPlay, Mediaset Infinity, Nintendo Switch; inputs HDMI 2 "Nintendo Switch", HDMI 3 "PC"; clock 24; source bing; layout list.
3. Verify with `adb exec-out screencap -p` + `input keyevent`: navigation List/Dock, Photo mode, All apps (inputs listed), Settings groups, launch each Home app and HDMI 2, Back/Home behaviour, Bing photos appear, rotation.
4. Reboot → comes up as Home. `dumpsys meminfo io.github.mdrzn.cleanframetv` ≤ ~150 MB; `logcat` clean.
5. Fix findings (repeat). Commit fixes.

### Task 12: Documentation and release prep

**Files:** `README.md`, `AGENTS.md`, `docs/development.md`, `docs/interaction-behaviour.md` (Photo mode, Android TV group, clock, source), `THIRD_PARTY_NOTICES.md`, `docs/install.md`.
1. README: what it is, credit to CleanFrame/Nathan Clark, screenshots from the TV, install (APK + ADB set-home), ADB config example, restore previous launcher, build from source.
2. Commit. Ask the owner before creating/pushing a public GitHub repo.
