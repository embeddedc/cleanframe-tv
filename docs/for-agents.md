# Working with a coding agent

CleanFrame TV was developed with a coding agent and is easy to adapt with one. Decide the scope first, because each needs a different level of access: an interface change (browser only), an Android change (build tools), or work on your own TV (ADB access to it).

## Change the interface

```text
Read AGENTS.md, docs/development.md and docs/interaction-behaviour.md.
Help me adapt CleanFrame TV. Start with the public-font browser preview
(npm run dev:public). My first change is: <describe one change>.
Preserve remote navigation, saved preferences and the ES5 runtime.
Run npm run check and the relevant browser checks. Show the result.
This task is browser-only: do not connect to a TV or install anything.
```

`app/main.js` handles interaction; `app/device.js` owns everything Android. Without the Android bridge the preview uses a mock catalogue, so device actions report that they need the TV.

## Change the Android launcher

```text
Read AGENTS.md and docs/development.md. In android/, <describe the change>.
Keep pure rules in unit-tested Kotlin without Android types, keep
permissions minimal and make no network requests unless the Bing photo
source is selected. Run ./gradlew testReleaseUnitTest assembleRelease.
Do not install on a device unless I say so.
```

## Install on your TV

Use a local agent that can run `adb` on your computer; a hosted chat cannot reach your TV.

```text
Read AGENTS.md and docs/install.md. Help me install CleanFrame TV on my
<TV or box model>. First check the ADB connection and note the current
home launcher so we can restore it. Announce each install, Home change,
reboot or input switch before doing it, and stop if I'm using the TV.
Then help me name my HDMI inputs and choose my Home apps.
```

Things an agent should know about TVs:

- `adb shell input keyevent` drives the interface but bypasses accessibility services, so remote-button assignments need the physical remote.
- Key presses sent while the owner holds the remote can land in another app. Check the foreground app (`dumpsys activity activities | grep mResumedActivity`) before sending keys.
- `adb shell screencap -p /sdcard/x.png` then `adb pull` is safer than `exec-out screencap`, which some vendors corrupt with log text.
- `--ez debug true` enables `chrome://inspect` for the launcher's page.

## Publishing

Making a repository public, uploading releases and rewriting history each need an explicit instruction from the owner. Never commit signing keys, keystore passwords, device addresses or APKs.
