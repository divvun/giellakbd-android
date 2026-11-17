# giellakbd-android

An Android keyboard for marginalised languages, built on Google's LatinIME (the AOSP keyboard). It uses [DivvunSpell](https://github.com/divvun/divvunspell) for spelling suggestions and [Pahkat](https://github.com/divvun/pahkat) to download and update spellers on the device.

## How it fits together

Keyboard layouts are written once per language as [kbdgen](https://github.com/divvun/kbdgen) bundles, in the `keyboard-*` repositories under [giellalt](https://github.com/giellalt) (for example [`sms.yaml`](https://github.com/giellalt/keyboard-sms/blob/main/sms.kbdgen/layouts/sms.yaml)). The same layout produces keyboards for Android, iOS, macOS, Windows and the other platforms kbdgen supports.

On Android, one app ships many languages. Two bundles define which languages go into which app, and each is published as its own app on Google Play:

- [divvun-dev-keyboard](https://github.com/divvun/divvun-dev-keyboard): the testing app.
- [divvun-keyboard](https://github.com/divvun/divvun-keyboard): the production app.

kbdgen turns a bundle into an Android project. It clones this repository, downloads the native libraries, generates the layouts, strings, input method subtypes and launcher icons, writes `app/local.gradle`, and runs `./gradlew assembleRelease`.

This repository alone doesn't produce a usable keyboard: the layouts, language resources and native libraries all come from kbdgen. To work on it, generate a project with kbdgen and work in that.

## Building

### Prerequisites

- **kbdgen**: install it as described in [kbdgen's README](https://github.com/divvun/kbdgen#installation).
- **ImageMagick**: kbdgen uses its `convert` command to resize the launcher icon. On macOS, `imagemagick@6` is known to work:

  ```sh
  brew install imagemagick@6
  echo 'export PATH="/opt/homebrew/opt/imagemagick@6/bin:$PATH"' >> ~/.zshrc
  ```

- **Android SDK and JDK 17 or newer**: Android Studio includes both.

### Generating a project

```sh
git clone https://github.com/divvun/divvun-dev-keyboard.git
kbdgen fetch --bundle-path divvun-dev-keyboard/divvun-dev.kbdgen
kbdgen target --bundle-path divvun-dev-keyboard/divvun-dev.kbdgen --output-path build/android android build
```

`kbdgen fetch` downloads the language layouts the combined bundle lists. `kbdgen target … android build` then generates the project in `build/android/repo` and runs an unsigned release build. Open `build/android/repo` in Android Studio.

To regenerate the layouts and resources without cloning again or downloading the native libraries, run the same `kbdgen target` command with `android generate` instead of `android build`.

### Making changes

The generated project is a git clone of this repository, so you can commit and push from it directly. `git status` will also list everything kbdgen generated. Commit only your own changes, not the generated files.

kbdgen always clones `main` from GitHub, so a change only reaches kbdgen builds (including releases) once it's merged to `main`.

## Native libraries

The app needs `libdivvunspell.so` and `libpahkat_client.so` in `app/src/main/jniLibs/<abi>/`. kbdgen downloads prebuilt copies from the GitHub releases of [divvunspell](https://github.com/divvun/divvunspell/releases) and [pahkat](https://github.com/divvun/pahkat/releases).

To build them yourself:

1. Install NDK 28 or 29 from Android Studio's SDK Manager, and install [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk`.
2. In a checkout of [divvunspell](https://github.com/divvun/divvunspell), check out the tag of the release kbdgen uses (currently `libdivvunspell/v1.0.0-beta.6`). Newer versions build a library with a different name, which this app doesn't load. Then run:

   ```sh
   cargo ndk -t armeabi-v7a -t arm64-v8a -o ./lib build -vv --lib --release --features internal_ffi
   ```

3. In a checkout of [pahkat](https://github.com/divvun/pahkat), go to `pahkat-client-core` and run:

   ```sh
   cargo ndk -t armeabi-v7a -t arm64-v8a -o ./lib build -vv --features ffi,prefix --release
   ```

4. Copy the contents of each `lib` directory into `app/src/main/jniLibs/`.

## Releases

This repository has no CI of its own. Releases are built on Buildkite by [divvun-actions](https://github.com/divvun/divvun-actions). Each `main` build of divvun-keyboard or divvun-dev-keyboard runs kbdgen, which picks up this repository's `main`, and uploads the signed APK to the alpha track on Google Play.

kbdgen writes the signing configuration to `app/local.gradle` from the `ANDROID_KEYSTORE`, `ANDROID_KEYALIAS`, `STORE_PW` and `KEY_PW` environment variables. For a signed build by hand, write `app/local.gradle` yourself (it's ignored by git):

```gradle
ext.app = [
    storeFile: "path/to/keystore.jks",
    keyAlias: "alias",
    storePassword: "password",
    keyPassword: "password",
    packageName: "com.example.package",
    versionCode: 1,
    versionName: "1.0.0"
]
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
