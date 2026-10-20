# Contributing to CleanFrame TV

CleanFrame TV has been tested on one TV. Contributions that keep the remote experience simple, make it work on more devices, and say plainly what was tested are welcome. Interface ideas that would suit the original Samsung app too are best proposed upstream at [CleanFrame](https://github.com/nthnclrk/CleanFrame).

## Start in the browser

Most interface work needs no TV:

```sh
npm ci
npm run dev:public
npm run check
npx playwright install chromium
CLEANFRAME_PUBLIC_FONTS=1 npm run check:browser
```

Keep runtime JavaScript in `app/` ES5-compatible, because Android TV boxes ship old WebViews. Use the existing focus, panel and preference logic, and keep platform details in `app/device.js`. Keep new dependencies in development tooling unless the runtime need is clear.

## Android changes

```sh
cd android
./gradlew testReleaseUnitTest assembleRelease
```

Put rules that don't need Android types in `Catalog.kt` (or a similar pure file) with unit tests. Keep permissions minimal: say in the pull request why any new permission or network request is needed. CleanFrame TV makes no network requests unless the Bing photo source is selected, and that should stay true.

## Propose a change

For a bug, describe the trigger, the expected result and the actual result, with the device model, Android version and WebView version (`adb shell dumpsys package com.google.android.webview | grep versionName`). Logs from `adb logcat -s CleanFrameTV:I CleanFrameJS:I` help. For a new feature, explain the task it helps someone complete from the sofa.

A pull request should explain the resulting behaviour, the checks run and anything not tested on a device. Include a screenshot for visual changes. Check both List and Dock, single focus, Back behaviour in panels and saved-preference recovery when relevant. The physical remote remains the final check for remote feel.

## Device work and privacy

Test on a TV or box you are allowed to experiment on, with the previous launcher still installed ([recovery](docs/install.md#going-back-or-uninstalling)). Keep IP addresses, serial numbers, account details, signing keys and passwords out of issues, screenshots, logs and pull requests. Report security issues privately through GitHub's private vulnerability reporting if it is enabled, not in a public issue.

## Assets and licences

Original code is MIT; contributions use the same licence. Keep upstream asset licences and source records intact. Add only assets you have permission to redistribute, with any required attribution. Review [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) before adding or replacing a logo, photograph or font. Photos fetched at runtime (such as Bing's) must not be committed.
