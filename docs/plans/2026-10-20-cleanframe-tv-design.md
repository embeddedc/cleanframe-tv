# CleanFrame TV — design

Date: 2026-10-20 · Status: validated with the owner, ready for an implementation plan

## Goal

Port [CleanFrame](https://github.com/nthnclrk/CleanFrame) (Nathan Clark, MIT) — a quiet,
remote-first home screen built for Samsung The Frame (Tizen) — to **Android TV** as a real
launcher app, and publish it as open source.

Reference device: Xiaomi Mi TV Q1 (MiTV-MOOQ1), Android TV 10 (API 29), MediaTek MT9632,
32-bit ARM userspace (armeabi-v7a), 1.8 GB RAM (`low_ram`), Android UI at 1920×1080 on a 4K panel.
It currently runs Projectivy Launcher as home.

## Decisions

| Topic | Decision |
|---|---|
| Repository | Separate repo `cleanframe-tv`, created from CleanFrame's git history (original commits and authorship kept). `upstream` remote kept for cherry-picking UI improvements by hand. |
| Platforms | **Android TV only.** All Samsung/Tizen code, scripts, docs, Art Mode and Samsung settings are removed. |
| App name | **CleanFrame TV** |
| Rendering | Native Android shell + full-screen **WebView** running CleanFrame's existing HTML/CSS/JS interface. |
| Art Mode | Replaced by **Photo mode**: hides the interface, shows the photo (and clock); any key returns. |
| HDMI inputs | Shown as entries like apps, launchable directly; nameable (e.g. "Nintendo Switch", "PC"). |
| Clock | Adds 24-hour format; default follows the Android system setting. |
| Language | English UI (upstream language; better for an open-source project). |
| Photos | Bundled 15 wallpapers (offline default) **plus Bing photo of the day** as an optional source. |
| Bing resolution | Download **4K (UHD)** originals; render at the device's real UI resolution (1080p on the reference TV, 4K where the UI runs at 4K). |
| Fallback | Projectivy stays installed; "Open previous launcher" escape in Settings; ADB one-liner to restore. |

## Architecture

```
cleanframe-tv/
  app/                 CleanFrame web interface (Tizen parts removed, Android adapter added)
  android/             Android shell (Kotlin, Gradle)
    src/main/assets/   ← web interface copied/linked from app/ at build time
  docs/                design, development, install guide
  scripts/             web checks kept from upstream (Tizen scripts removed)
```

### Android shell

- **`HomeActivity`** — `singleTask`, intent filters `MAIN` + `HOME` + `DEFAULT` + `LEANBACK_LAUNCHER`,
  TV banner. Hosts one full-screen `WebView` kept alive across app launches (no reload on Home).
  Back on the Home does nothing; deeper screens handle Back in JavaScript as upstream does.
- **Asset serving** — `androidx.webkit.WebViewAssetLoader` on `https://appassets.androidplatform.net`:
  `/assets/` → bundled web UI; `/photos/` → cached Bing images and their generated materials.
  A real HTTPS origin keeps `localStorage` working and avoids `file://` restrictions.
- **JS bridge** `window.CleanFrameAndroid` (`@JavascriptInterface`), replacing `device.js`'s Tizen calls:
  - `appsJson()` → installed launchable apps (`LEANBACK_LAUNCHER`, falling back to `LAUNCHER`) and
    HDMI inputs from `TvInputManager` (tuner/composite/SCART/VGA hidden).
    Items: `{ id, name, kind: "app"|"input", brand? }`; inputs use `id = "input:<inputId>"`.
  - `launch(id)` → apps via `getLeanbackLaunchIntentForPackage`; inputs via
    `ACTION_VIEW` on `TvContract.buildChannelUriForPassthroughInput(inputId)`
    (verified on the reference TV: HDMI 2 opened directly in the MediaTek TV app).
  - `openSettings()` (`Settings.ACTION_SETTINGS`), `openPreviousLauncher()`, `is24Hour()`.
  - Photo feed: `photosJson()`, `refreshPhotos()`.
  - Native → JS events: `cleanframe:apps-changed` (package added/removed/replaced broadcast),
    `cleanframe:photos-updated`, `cleanframe:launch-failed`.
- **Config via ADB** (setup convenience, also documented for users):
  `am start -n <pkg>/.HomeActivity --es config '<json>'` merges preferences (e.g. input names,
  Home apps) into `localStorage` on start.
- **Permissions** — `INTERNET` (Bing only). No `QUERY_ALL_PACKAGES`; a `<queries>` block declares the
  launcher intents for package visibility on API 30+. minSdk 26, targetSdk/compileSdk 36.

### Web interface changes (`app/`)

- `device.js` becomes the Android adapter: implements the same `FrameDevice` surface the UI already
  calls (`launch`, `launchApp`, `apps`, `settings`) on top of the bridge; `art()` and `home()` removed.
- Art Mode control → **Photo mode** (UI state only: hide panels/list, keep photo + clock, optional
  Bing caption; any key restores focus where it was).
- Settings: the "Samsung" group becomes **"Android TV"**: TV settings, Inputs (rename HDMI inputs),
  Open previous launcher. Background group gains **Source: Built-in / Bing photo of the day**.
- Clock: 12/24-hour option, default from `is24Hour()`.
- Brand icons: map Android package names to brand glyphs. New monochrome glyphs from Simple Icons
  (CC0) where available: Jellyfin, Twitch, Nintendo Switch (Prime Video, Netflix, YouTube exist).
  Mapping includes `org.smarttube.stable` → YouTube, `com.s0und.s0undtv` → Twitch. Unknown apps show
  the name only.
- Browser preview keeps working with a mock bridge (fake apps and inputs).

### Default Home list (reference TV)

Prime Video · YouTube (SmartTube) · Jellyfin · Twitch (s0undTV) · RaiPlay · Mediaset Infinity ·
Nintendo Switch (HDMI 2). "PC" is HDMI 3 (All apps). Editable on the TV (1–9 apps).

## Bing photo of the day

- Feed: `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=<device locale>`.
  Image: `https://www.bing.com<urlbase>_UHD.jpg` (fallback `_1920x1080.jpg`). Caption from `copyright`.
- Checked at most every 12 hours, triggered on Home resume, on a background thread. Keeps the last 8.
- Per new photo, once: store the 4K original, a display-size copy (real UI resolution), the blurred /
  frosted / soft material variants the UI expects, and a light/dark **ink** decision computed from
  luminance under the text regions (mirrors the precomputed values of bundled wallpapers).
- Rotation uses upstream's existing modes (fixed / each time Home opens / 5 / 15 / 60 minutes).
- Failures never block the UI: no network → cached photos; empty cache → bundled wallpapers.
- Images are fetched at runtime for wallpaper use and not redistributed in the repo or APK.

## Error handling

| Case | Behaviour |
|---|---|
| App uninstalled / input unplugged | Short on-screen message ("Couldn't open …"), stay on Home; list refreshes on package change. |
| WebView renderer crash | Recreate the WebView and reload (`onRenderProcessGone`), restoring preferences from storage. |
| Bing unreachable or malformed | Keep previous photos; fall back to bundled; log only. |
| Low memory | Release the hidden WebView's caches on `onTrimMemory`; Home stays a foreground-priority process. |
| Launcher broken after install | Projectivy remains installed; `cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity` restores it. |

## Testing

- Web: upstream source checks (`npm run check`) and browser checks with the mock bridge (Tizen-only checks removed).
- Android: unit tests for package→brand mapping, input filtering/naming, Bing JSON parsing, ink computation.
- On the reference TV: remote navigation (List and Dock), launching each Home app and HDMI 2/3,
  Photo mode, rotation with Bing and bundled photos, reboot (comes up as Home), `dumpsys meminfo`
  target ≤ Projectivy (~100–150 MB PSS), no crashes in `logcat`.

## Publication

- Repo private until verified on the reference TV; owner approves before making it public.
- README: credit to CleanFrame / Nathan Clark, screenshots, APK from GitHub Releases, install guide
  (`adb install`, set as home), ADB config example, uninstall/restore steps.
- License: MIT (upstream's), plus third-party notices for fonts (OFL), icons (Simple Icons CC0 and
  upstream sources) and bundled wallpapers (upstream SOURCES.json).
- Release signing key lives outside the repo, with a backup; losing it prevents in-place updates.

## Later (not in v1)

- Register as an Android screensaver (DreamService) showing the same photos.
- Wikimedia Picture of the Day and Jellyfin backdrops as photo sources.
