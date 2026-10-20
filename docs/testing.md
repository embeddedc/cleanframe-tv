# Validation

## Interface checks

```sh
npm ci
npm run check
npx playwright install chromium
npm run check:browser
```

`check` validates JavaScript syntax, the Android adapter (`scripts/test-device.mjs`: brand mapping, catalogue, launch/close results, configuration, browser fallback) and the bundled resources. `check:browser` runs the real interface in a fresh Playwright Chromium profile with a fake `window.CleanFrameAndroid` bridge and its own loopback preview. It covers List and Dock navigation, single focus, Home controls, Photo mode, grouped Settings including the Android TV group, explicit OK application, applied checkmarks, pending-load cancellation, retries, persistence, all fifteen wallpaper bundles and the photo source switch, fonts, the 12/24-hour clock, 720p scaling, migrations, the Home editor, HDMI inputs, hold-to-close and honest browser-only errors. Reports and screenshots go to ignored `.local/qa/`. A failed assertion exits with a nonzero status. Set `CLEANFRAME_PUBLIC_FONTS=1` to exclude any local private fonts.

If the default browser cache is unavailable, use a writable path for both commands:

```sh
PLAYWRIGHT_BROWSERS_PATH="$PWD/.local/playwright" npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH="$PWD/.local/playwright" npm run check:browser
```

For manual review, use `npm run dev` and open `/home/` with the arrow keys, Enter and Escape. Check a changed flow in List and Dock, on Photo, Light and Dark backgrounds, with one app and with nine. In Settings, browse without applying, apply with OK, return with Back, and cancel a pending load. Browser preferences are separate from the TV's.

## Android checks

```sh
cd android
./gradlew testReleaseUnitTest assembleRelease
```

The unit tests cover the catalogue rules (deduplication, TV activities, hidden system entries, HDMI filtering and naming), configuration and button parsing, Bing feed parsing and the photo ink decisions. `assembleRelease` also runs Android lint's release checks.

## On a TV

These checks need a TV or box you are allowed to experiment on. Keep the previous launcher installed and note how to restore it ([install guide](install.md#going-back-or-uninstalling)).

1. Press Home from another app: CleanFrame TV appears, with the focus where you left it.
2. Move through both layouts and Settings with the remote; check focus, response and readable labels from the sofa.
3. Open each Home app and every HDMI input, then press Home to return. Hold OK on an app and confirm "Closed …"; `adb shell pidof <package>` shows whether its process ended.
4. Photo mode: enter, then any key returns with focus restored.
5. With Photo source set to Bing, check that photos arrive (`adb logcat -s CleanFrameTV:I` shows "Bing photo ready") and rotation works. With built-in wallpapers, no requests should be made.
6. Restart the TV: it should come up on CleanFrame TV, with saved appearance, Home order and photo.
7. If remote buttons are assigned, press each with the physical remote.
8. `adb shell dumpsys meminfo io.github.mdrzn.cleanframetv` and `adb logcat` for crashes (`AndroidRuntime`, `chromium` errors).

Record the device, Android version, WebView version and results when reporting.
