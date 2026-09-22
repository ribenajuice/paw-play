#!/usr/bin/env bash
# Single build/release entrypoint — CI runs exactly this, and so can you.
# The /kickoff (or /deploy) workflow replaces the placeholder below with the
# real signed-build commands for the engine chosen in docs/ARCHITECTURE.md.
#
# Expected env vars when signing (set by CI from repo secrets, or export
# them yourself locally): ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD,
# ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD.
set -euo pipefail

echo "❌ deploy.sh has not been configured yet."
echo "   Run /deploy in Claude Code — it will wire this up for your engine."
exit 1

# --- Examples the devops-engineer agent may adapt ---
# Gradle (Kotlin/Compose):
#   ./gradlew bundleRelease \
#     -Pandroid.injected.signing.store.file="$ANDROID_KEYSTORE_PATH" \
#     -Pandroid.injected.signing.store.password="$ANDROID_KEYSTORE_PASSWORD" \
#     -Pandroid.injected.signing.key.alias="$ANDROID_KEY_ALIAS" \
#     -Pandroid.injected.signing.key.password="$ANDROID_KEY_PASSWORD"
#
# Godot (export presets defined in export_presets.cfg):
#   godot --headless --export-release "Android" build/game.apk
