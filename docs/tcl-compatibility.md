# TCL / Google TV compatibility preview

This preview has been tested on one TCL Smart TV Pro running Android 11. It supports direct Home
selection after manual ADB setup, or an optional redirect while Google TV stays enabled. The app
itself never changes package enabled states or system Home preferences. It installs separately
from the upstream release and starts with fresh settings. Other firmware remains unverified.

## Evidence and limits

The reported TCL Smart TV Pro runs Android 11 (API 30), with a 32-bit ARM userspace and a valid
Google WebView provider. Its HOME candidates are:

| Handler | Intent priority |
| --- | ---: |
| Google TV `VanillaModeHomeActivity` | 2 |
| Google setup `RecoveryActivity` | 1 |
| CleanFrame `HomeActivity` | 0 |
| Android TV settings `FallbackHome` | -1000 |

After `set-home-activity` reported success, HOME still resolved to Google TV. Disabling Google TV
resulted in a stuck screen; restoring it recovered the TV. The capture has no CleanFrame exit record
or relevant crash log, so it does **not** establish a CleanFrame crash or what displayed while the
screen was stuck. The remaining higher-priority setup handler is a possible explanation.
Do not commit device addresses, raw device logs, signing keys or APKs.

Live ADB inspection subsequently confirmed that CleanFrame renders normally and its in-app
compatibility switch was on, but Android had no accessibility services enabled. The standard
`android.settings.ACCESSIBILITY_SETTINGS` action resolved to
`com.google.android.tv.frameworkpackagestubs/.Stubs$SettingsStub`, which did not open a settings
screen. `android.settings.ACCESSIBILITY_TV_OEM_LINK` opened the real TCL Accessibility screen.
The app now prefers that exported OEM route when available, falls back to standard Android
settings elsewhere, and skips the known placeholder package.

After installing this fix, the service was enabled but initially unbound. Restarting only its
accessibility registration connected it. Live checks on this TCL then confirmed:

- The preview's Accessibility button opens `.oemlink.AccessibilitySettingsActivity`.
- An ADB Home press returns to the preview, including from Android Accessibility settings.
- The service logs the redirect, its stored opt-in remains enabled, and a captured TV screenshot
  shows the working CleanFrame interface. Google TV remains enabled throughout.

The owner also confirmed that the physical remote's Home button redirects to CleanFrame.
Normal Home presses appear smooth on the tested TV, but returning from Settings can briefly show
Google TV before the redirect. This preview does not intercept the physical Home key directly.

A later controlled test disabled LauncherX for user 0. Home from Settings returned to the already
running CleanFrame, although Android's HOME resolver selected Google's `RecoveryActivity`.
After rebooting in that state, boot completed but LauncherX was enabled again and foregrounded.
The compatibility opt-in and enabled-service entry survived, but `accessibility_enabled` was 0
and the service was not bound. Reconnecting the accessibility registration restored Home redirection
from Settings. The retained logs do not identify what changed the package state; they do not prove
that a watchdog or bootloader lock caused it.

The owner subsequently disabled **both** `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` and reported success across reboot. ADB inspection
confirmed both packages disabled, HOME resolving directly to this preview's `HomeActivity`, and
Home from Android Settings returning to CleanFrame. The reboot result is owner-reported; no second
automated reboot was performed. The existing compatibility service was still enabled during the
inspection, but its Google TV event target was disabled. This is not proof of compatibility with
other TCL models, future firmware updates, or long-term use.

The separate TCL Guard check found `AUTO_START` unsupported but `APP_AUTO_START` supported and
set to `ignore` for this preview. `APP_ASSOC_START` also defaulted to `ignore`. These permissions
were not changed for the successful direct-Home check. Process protection and Home resolution
are separate concerns; neither startup permission nor a Guard whitelist is established here as
the cause of LauncherX's earlier re-enabling.

## How the optional redirect works

- Settings → Android TV → **Home button compatibility** opens a native setup/recovery screen.
- Compatibility is off by default. Both the in-app switch and the separate Android accessibility
  service must be enabled by the user.
- The service receives only Google TV window-state events and matches its exact `HomeActivity`
  and `VanillaModeHomeActivity` names. It ignores profiles, dashboard, setup, other apps and key
  presses. It cannot retrieve screen contents, does not poll, and uses no network.
- When one of these Home windows appears, it explicitly opens this build's `HomeActivity` with a
  HOME intent. Android's default can remain Google TV. Google TV may flash briefly before CleanFrame.
  This also applies when Back returns to Google TV; it is not a physical Home-key remapper.
- No redirects while the screen is off, the keyguard is locked, or CleanFrame is already visible.
  Duplicate events are suppressed for 1.5 seconds. More than three attempts in ten seconds pause
  the feature. Attempt times survive process restarts; a backward clock change also pauses it.
- Opening **Previous launcher**, or explicitly launching Google TV from CleanFrame, turns
  compatibility off so the original home screen remains usable.
- The existing Remote buttons service retains its original permissions and behavior.

The fallback depends on firmware delivering window events and permitting the service to open
CleanFrame. Test on the actual TV before claiming support. Unknown Google TV activity names are
intentionally ignored. The diagnostic report alone does not validate this implementation.

## Build on the Mac

```sh
bash scripts/build-tcl-preview.sh
```

The helper downloads checksum-verified official command-line tools if needed, asks for SDK license
acceptance, installs platform 36/build tools 36.1.0, installs pinned npm dependencies and Chromium,
runs web/browser checks, then runs `testReleaseUnitTest assembleRelease assembleDebug`.
JDK 21, Node.js 22+ and Python 3.10+ are required. Caches live in ignored `.local/`.
The log is `.local/tcl-build.log`; the output is `dist/CleanFrameTV-TCL-preview.apk`.
Signing uses the local Android debug keystore outside this repository; retain it for updates.

## Install and test

Replace `<tv-address>:<port>` with the connected TV's ADB address. Install and open the preview
before changing Home routing. Confirm remote navigation, All apps and TV settings work.

```sh
adb -s <tv-address>:<port> install -r dist/CleanFrameTV-TCL-preview.apk
adb -s <tv-address>:<port> shell am start -n io.github.mdrzn.cleanframetv.tcl/io.github.mdrzn.cleanframetv.HomeActivity
```

The package ends in `.tcl`, but Kotlin activity/service names retain their original namespace.
Do not use `.HomeActivity` shorthand with the preview package.

### Direct Home on the tested TCL

The owner used these two commands after confirming CleanFrame works and ADB is available:

```sh
adb -s <tv-address>:<port> shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s <tv-address>:<port> shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Disabling only LauncherX left Google's higher-priority recovery activity as the HOME candidate.
With both disabled on this TV, verify resolution before testing Home or rebooting:

```sh
adb -s <tv-address>:<port> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Expected component:

```text
io.github.mdrzn.cleanframetv.tcl/io.github.mdrzn.cleanframetv.HomeActivity
```

Test the physical Home button from Settings and another app. For a reboot test, allow the package
changes time to persist, then recheck disabled state and Home resolution after boot. The owner
reports persistence on this TV. Keep the recovery commands below available: these commands also
disable Google's setup/recovery UI, and behavior may differ on other firmware. Both packages remain
installed and can be re-enabled. The app's **Previous launcher** button cannot re-enable them.

### Optional redirect with Google TV enabled

Use this route when keeping both Google packages enabled. It is separate from direct Home setup.

1. Verify the preview opens and remote navigation, All apps and TV settings work normally.
2. Open Settings → Android TV → Home button compatibility. Select **Turn on compatibility**.
3. Open Accessibility settings and manually enable **CleanFrame TCL Home compatibility**.
   Return to the setup screen and confirm it says compatibility is on.
   After an APK update, if Android shows the service enabled but CleanFrame still asks to enable
   it, turn that service off and on once in Accessibility settings to reconnect it.
4. Return to CleanFrame and press physical Home. Repeat from another app. Check Back from an app,
   screen off/on, and reboot. Record results rather than assuming success.
5. Open Previous launcher: compatibility must turn off and Google TV remain usable. Re-enable
   manually for further testing. Check that Google TV profiles/dashboard and TV settings are not
   redirected.
6. If compatibility pauses after rapid switching, inspect its setup screen and logs before retrying.

Relevant log:

```sh
adb -s <tv-address>:<port> logcat -d -s CleanFrameTV:I AndroidRuntime:E
```

## Recovery

If using direct Home, first restore both Google packages before removing CleanFrame or attempting
to return to Google TV:

```sh
adb -s <tv-address>:<port> shell pm enable --user 0 com.google.android.tungsten.setupwraith
adb -s <tv-address>:<port> shell pm enable --user 0 com.google.android.apps.tv.launcherx
```

Turn off **CleanFrame TCL Home compatibility** in Android Accessibility settings, or open the
native setup screen directly (it does not need a working WebView):

```sh
adb -s <tv-address>:<port> shell am start -n io.github.mdrzn.cleanframetv.tcl/io.github.mdrzn.cleanframetv.HomeSettingsActivity
```

Select **Turn off compatibility and open previous launcher**. If unavailable, stop only the preview
to prevent redirects until it is explicitly reopened, then open Google TV:

```sh
adb -s <tv-address>:<port> shell am force-stop io.github.mdrzn.cleanframetv.tcl
adb -s <tv-address>:<port> shell am start -n com.google.android.apps.tv.launcherx/.home.VanillaModeHomeActivity
```

To remove the preview and its test settings, uninstall `io.github.mdrzn.cleanframetv.tcl`.
This leaves the upstream CleanFrame installation intact. Uninstalling the preview does not
re-enable packages disabled through ADB; restore them first as shown above.

## References

- [Android accessibility services](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Window-state events](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED)
- [Official Android SDK tools and checksums](https://developer.android.com/studio#command-tools)
