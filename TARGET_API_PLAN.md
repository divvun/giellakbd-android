# Plan: Resolve Google Play target API level requirement

## Background

Google Play flagged 2 apps built from this repo as targeting an old Android
version. Play requires meeting target API level requirements before
**31 Aug 2026**.

**Root cause:** `app/build.gradle` sets `targetSdkVersion 35` (Android 15).
Play's 2026 deadline requires **API 36 (Android 16)**. `compileSdkVersion` is
already 36, so this is a small, low-risk bump rather than a full migration.

**Why "2 apps":** this repo is a shared codebase — each language keyboard
(e.g. Northern Sámi) is built as a separate Play Store listing via
`local.gradle` / `applicationId app.packageName`, all sharing this one
`app/build.gradle`. A fix here should cover every app built from this
repo/pipeline. Confirm in Play Console that both flagged apps are actually
built from here.

## Steps

0. **Local dev environment (done):**
   - [x] Fixed Android Studio "Incompatible Gradle JVM" error — the
     project's `jbr-21` JDK registry entry was stale and actually resolved
     to the same path as the bundled `jbr-25` runtime. Installed a real
     JDK 21 via `brew install openjdk@21` and pointed Gradle JDK at
     `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
   - [x] Applied Android Studio's recommended Gradle minor update,
     8.13 → 8.14.5 (also switched the wrapper distribution from `-all` to
     `-bin`). Confirmed this needs no `divvun-actions` CI changes: the
     wrapper is fully self-contained (`gradlew`/`gradlew.bat`/
     `gradle-wrapper.jar`/`gradle-wrapper.properties` all committed here),
     the CI Linux image's `openjdk-17-jdk` is compatible with both Gradle
     versions, and nothing in `divvun-actions` pins a separate Gradle
     install.

1. [x] **Bump targetSdkVersion 35 → 36** in `app/build.gradle:40`. Done;
   `./gradlew assembleDebug` builds clean on this branch.

2. **Behavior-change pass for Android 16 targeting** (IME + spell-checker
   with foreground services):
   - Predictive back gesture — verify keyboard/IME UI and
     `SetupActivity` / `PersonalDictionaryActivity` back navigation still
     work (Android 16 enforces predictive back more strictly at target 36).
   - Edge-to-edge is already enforced by default at target 35 — re-verify
     keyboard view insets/padding aren't broken by any 36-specific insets
     changes.
   - `foregroundServiceType="specialUse"` usage (`LatinIME`,
     `DivvunSpellCheckerService`) — confirm these still start correctly;
     Android 15/16 tightened FGS start restrictions from background.
   - Review the official "Behavior changes: apps targeting Android 16" doc
     for anything else relevant to IMEs.

3. **16 KB page size check (separate but related Play requirement).** This
   app ships native libs — `libpahkat_client.so`, `libdivvunspell.so`
   (armeabi-v7a + arm64-v8a). Play also requires apps with native code to
   support 16 KB page sizes. Verify these `.so` files (built from the Rust
   `divvunspell` / `pahkat-client` sources) are 16 KB-aligned; if not, this
   is a second, independent blocker to fix alongside the target API bump.

4. [x] **CI: update the `divvun-actions` build image's Android SDK
   platform.** `divvun-actions/docker/tools/android-sdk.ts` defaulted to
   `platform: "android-35"` / `buildTools: "35.0.1"`, and
   `divvun-actions/docker/images/linux.ts:138` calls `androidSdk()` with no
   overrides — so the CI Linux image only guaranteed platform 35 was
   present, not 36. `compileSdkVersion` has been 36 since `eadfbc3`
   (2025-08-26), predating this gap, so it was likely surviving on AGP's
   just-in-time SDK auto-download (licenses are pre-accepted at
   image-build time via `sdkmanager --licenses`). Bumped the defaults to
   `android-36` / `36.0.0` (matches the build-tools version AGP resolves
   locally for `compileSdkVersion 36` with no explicit
   `buildToolsVersion` set) in `divvun-actions` branch
   `android-sdk-36-ci`, and regenerated `docker/Dockerfile.linux` via
   `deno task docker:gen`. Not yet committed/pushed/PR'd.

5. **Build & smoke test:**
   - `./gradlew assembleDebug` (or the relevant flavor) to confirm it
     compiles against the new target.
   - Install and manually exercise: keyboard show/hide, personal
     dictionary activity, spell checker suggestions, setup/permissions
     flow — ideally on an Android 16 emulator/device, since that's where
     target-36 behavior changes actually activate.

6. **Release:** bump `versionCode` / `versionName` per the normal release
   process (via `local.gradle` / CI) and roll out through Play Console
   (alpha track already wired via `com.github.triplet.play`) with enough
   buffer before 31 Aug 2026 for review and staged rollout.
