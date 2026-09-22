#!/usr/bin/env bash
# Single build/release entrypoint — CI runs exactly this, and so can you.
#
# Uses ./gradlew if it's present and executable (Android Studio generates
# it on first project open), otherwise falls back to a `gradle` on PATH
# (what CI uses via gradle/actions/setup-gradle — see .github/workflows).
#
# With no signing configured, this produces a debug APK, installable via
# `adb install` or by copying it to the phone. Once a release keystore is
# set up (run /deploy for first-time setup), it produces a signed release
# AAB instead — set ANDROID_KEYSTORE_PATH/_PASSWORD, ANDROID_KEY_ALIAS,
# ANDROID_KEY_PASSWORD to opt into that path.
set -euo pipefail

if [ ! -f settings.gradle.kts ]; then
  echo "❌ No Gradle project found yet. Run /kickoff (or /deploy) to scaffold it."
  exit 1
fi

GRADLE_CMD="gradle"
if [ -x "./gradlew" ]; then
  GRADLE_CMD="./gradlew"
fi

if [ -n "${ANDROID_KEYSTORE_PATH:-}" ] && [ -f "${ANDROID_KEYSTORE_PATH}" ]; then
  echo "Building signed release AAB..."
  "$GRADLE_CMD" bundleRelease \
    -Pandroid.injected.signing.store.file="$ANDROID_KEYSTORE_PATH" \
    -Pandroid.injected.signing.store.password="$ANDROID_KEYSTORE_PASSWORD" \
    -Pandroid.injected.signing.key.alias="$ANDROID_KEY_ALIAS" \
    -Pandroid.injected.signing.key.password="$ANDROID_KEY_PASSWORD"
  echo "✅ Signed AAB at app/build/outputs/bundle/release/app-release.aab"
else
  echo "No signing keystore configured yet — building a debug APK instead."
  echo "(Run /deploy for first-time signing setup when you're ready for a real release.)"
  "$GRADLE_CMD" assembleDebug
  echo "✅ Debug APK at app/build/outputs/apk/debug/app-debug.apk"
fi
