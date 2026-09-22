---
name: deploy
description: Build a signed release from the current main branch and verify it — without the full ship ceremony. Also handles first-time signing/keystore setup for the project.
---

# Deploy (build & release)

## First-time setup (if signing isn't wired up yet)
Signs: no release keystore secret in the repo (`gh secret list`), or `scripts/deploy.sh` still contains the scaffold placeholder.
1. Generate a release keystore locally (`keytool -genkeypair ...` or the engine's equivalent) — this file is the app's permanent identity; losing it means future updates can never be signed the same way again. Never commit it.
2. Store it as GitHub Actions encrypted secrets: the base64-encoded keystore file plus store/key passwords (`gh secret set ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`).
3. Have the devops-engineer agent make `scripts/deploy.sh` actually produce a signed AAB/APK per `docs/ARCHITECTURE.md`, decoding the keystore from the secret at build time and never writing it to a log.
4. Tell the founder plainly: back up the keystore file and its passwords somewhere outside the repo (password manager). This is the one credential in this whole project that can't be rotated after the fact for an existing app listing.

## Normal build
1. Confirm main is green in CI. Never release from a red main.
2. Trigger the pipeline: `gh workflow run deploy.yml && gh run watch` (preferred — same path every time). Local `scripts/deploy.sh` only when the founder asks or Actions is down.
3. Verify: download the resulting APK/AAB artifact, install it on a device or emulator, and confirm the newest change is actually there and the app launches into a working game. A build isn't done until verified on-device.
4. Report: what version (versionCode/versionName) was built, where to download it, and the verification you did.

## Rules
- Builds are idempotent; if a build half-fails, re-running must be safe.
- The signing keystore never appears in logs, commit history, or a build artifact — only the signed output does.
- Publishing to the Play Store (moving a build to internal testing, production, etc.) always needs explicit founder confirmation first; a downloadable APK for sideloading needs no such confirmation.
