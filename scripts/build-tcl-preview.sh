#!/usr/bin/env bash
# Run in a normal terminal: this downloads build tools, tests, and builds. It never contacts a TV.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
mkdir -p .local
exec > >(tee "$project_root/.local/tcl-build.log") 2>&1

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
export GRADLE_USER_HOME="$project_root/.local/gradle"
export PLAYWRIGHT_BROWSERS_PATH="$project_root/.local/playwright"
export npm_config_cache="$project_root/.local/npm-cache"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$project_root/.local/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

sdk_manager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdk_manager" ]]; then
    # Official Android command-line tools and SHA-256 from developer.android.com/studio.
    case "$(uname -s)-$(uname -m)" in
        Darwin-arm64)
            tools_zip=commandlinetools-mac_arm64-15859902_latest.zip
            tools_sha=835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e ;;
        Darwin-x86_64)
            tools_zip=commandlinetools-mac_x86_64-15859902_latest.zip
            tools_sha=c5a6378ab5cf7e0d5701921405115befff13e9ff7417fb588389338f8bd050f3 ;;
        *)
            echo 'Set ANDROID_HOME to an SDK with cmdline-tools/latest/bin/sdkmanager.' >&2
            exit 1 ;;
    esac
    echo 'Android SDK tools require the SDK license: https://developer.android.com/studio#terms'
    read -r -p 'Have you reviewed and accepted these terms to download the SDK tools? [y/N] ' sdk_consent
    case "$sdk_consent" in y|Y|yes|YES) ;; *) echo 'Stopped before downloading SDK tools.'; exit 1 ;; esac
    mkdir -p .local/downloads "$ANDROID_HOME/cmdline-tools"
    curl --fail --location --retry 2 "https://dl.google.com/android/repository/$tools_zip" -o ".local/downloads/$tools_zip"
    printf '%s  %s\n' "$tools_sha" ".local/downloads/$tools_zip" | shasum -a 256 -c -
    tools_tmp="$(mktemp -d "$project_root/.local/sdk-tools.XXXXXX")"
    trap 'rm -rf "$tools_tmp"' EXIT
    unzip -q ".local/downloads/$tools_zip" -d "$tools_tmp"
    # Never merge a download into a partially installed SDK tools directory.
    if [[ -e "$ANDROID_HOME/cmdline-tools/latest" ]]; then
        echo "SDK tools directory already exists but sdkmanager is missing: $ANDROID_HOME/cmdline-tools/latest" >&2
        exit 1
    fi
    mv "$tools_tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

"$sdk_manager" --sdk_root="$ANDROID_HOME" --licenses
"$sdk_manager" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;36.1.0' 'platform-tools'
npm ci
npm run check
npx --no-install playwright install chromium
npm run check:browser
./android/gradlew -p android testReleaseUnitTest assembleRelease assembleDebug --no-daemon
mkdir -p dist
cp android/app/build/outputs/apk/debug/app-debug.apk dist/CleanFrameTV-TCL-preview.apk
shasum -a 256 dist/CleanFrameTV-TCL-preview.apk
echo "Preview APK: $project_root/dist/CleanFrameTV-TCL-preview.apk"
echo 'No TV changes were made. See docs/tcl-compatibility.md for manual setup and recovery.'
