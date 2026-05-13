## E-Ink Launcher

`E-Ink Launcher` is a minimal Android launcher optimized for electronic-paper devices
(Onyx Boox, Supernote, Pocketbook, etc.).

This fork is maintained for personal use and modernized to target Android 16
(`compileSdk=36`, `minSdk=21`).

![E-Ink Launcher](app/release/preview.png)

### Features

- Grid layout with adjustable density and font size
- Built-in app search (long-press ⚙)
- App folders (single-cell composite icons)
- Hide / sort apps + dedicated Hidden Apps Manager
- One-tap lock / Wi-Fi / Bluetooth shortcuts
- Bluetooth quick-connect with last-used timestamps
- Custom TTF font via SAF picker
- Force language override
- Settings export / import (JSON)
- Auto periodic screen refresh
- Manifest app shortcuts
- Optional notification badges
- E-ink vendor fast-refresh reflection (Onyx / Supernote / MIUI)

### Flavors

| Flavor | Audience | Filtering |
|---|---|---|
| `generic` | Any Android e-ink reader | Only theme icon packs and self |
| `supernote` | Supernote / Chauvet OS | Also hides Chauvet internal services |

### Releases

Download from the [Releases page](https://github.com/hitobias/E-Ink-Launcher/releases).
Each release contains two APKs:
- `eink-launcher-v*-generic.apk` — for all Android e-ink devices
- `eink-launcher-v*-supernote.apk` — Supernote-tuned

### Building from source

```bash
brew install --cask temurin@17 android-commandlinetools

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
yes | sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"

git clone https://github.com/hitobias/E-Ink-Launcher.git
cd E-Ink-Launcher
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleGenericRelease assembleSupernoteRelease
```

APKs land in `app/build/outputs/apk/{generic,supernote}/release/`.

### CI

GitHub Actions builds both flavors on every push and runs unit tests.
Pushing a tag like `v0.2.0` triggers a Release build that publishes signed APKs
(requires `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` repo secrets).

### Acknowledgements

Original author: [Modificator](https://github.com/Modificator/E-Ink-Launcher).
This fork rewires the project to AndroidX, modernizes the architecture, and
adds the features listed above.

### License

Apache 2.0
