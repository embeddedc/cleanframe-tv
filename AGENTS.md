# CleanFrame TV

An Android TV launcher built from [CleanFrame](https://github.com/nthnclrk/CleanFrame) by Nathan Clark.

- `app/` is the web interface (HTML, CSS and ES5-compatible JavaScript). It runs inside the Android app's WebView and in the browser preview (`npm run dev`).
- `app/device.js` is the only place that talks to Android, through the synchronous `window.CleanFrameAndroid` bridge. Keep platform details there.
- `android/` is the Kotlin launcher shell (Gradle). It serves `app/` with `WebViewAssetLoader`, implements the bridge, lists apps and HDMI inputs, and prepares Bing photos.

Read `docs/development.md` and `docs/interaction-behaviour.md` before UI changes; `docs/install.md` covers the TV side. Preserve remote focus/navigation, preference migrations (`cleanframe.home.v1`) and pre-rendered materials: keep live blur and pixel analysis off the animation path.

Run `npm run check` (and `npm run check:browser` for behaviour changes) for UI changes, and `cd android && ./gradlew testReleaseUnitTest assembleRelease` for Android changes. CleanFrame TV makes no network requests unless the Bing photo source is selected; keep it that way.

Signing keys live outside the repository (`~/.config/cleanframe-tv/`). Never commit keys, passwords, device addresses or generated APKs. Device installs, Home changes and publication need an explicit instruction from the owner.
