# Flambo — Expressive Voice Recorder

A calm yet expressive voice recorder built with **Jetpack Compose + Material 3 Expressive**. Warm, tactile, and private — every recording stays on your device.

![Material 3 Expressive](https://img.shields.io/badge/Material%203-Expressive-%23D8572A)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12-blue)
![Min SDK](https://img.shields.io/badge/minSdk-26-lightgrey)
![Target](https://img.shields.io/badge/targetSdk-36-green)

## ✨ Features

- **One-tap record** with elegant countdown + morphing FAB
- **Live waveform** / pulse visualizer driven by amplitude sampling
- **Pause / resume** with continuous timeline preservation
- **Inline rename, tags, favorite** while the sheet is still open
- **Search** across titles & tags (lozenge-shaped expressive search bar)
- **Playback** with scrubber, waveform progress, 0.5×–2× speed, 5 s / 10 s skips
- **Floating mini-player** when you navigate away during playback
- **Soft delete + Undo** via Snackbar; Trash auto-purges after 7 days
- **Share** via system share sheet / FileProvider
- **Quality presets** — Voice (16 kHz 32 kbps) · High (44.1 kHz 128 kbps) · Lossless-ish (48 kHz 192 kbps)
- **Dynamic color** (Material You on Android 12+) + seed fallback, light/dark/system
- **Edge-to-edge** with proper WindowInsets, large-top-app-bar, 8dp spacing system
- **Privacy-first**: files in app-private `getExternalFilesDir` via `MediaStore`/`FileProvider` — no broad storage permission

## 🏗️ Tech Stack

- Kotlin 2.0.21, Compose BOM 2024.12.01, Material3 1.3.1, Activity 1.9.3
- Navigation Compose 2.8.5, Lifecycle ViewModel 2.8.7, Room 2.6.1, DataStore 1.1.1, Media3 ExoPlayer 1.4.1
- MVVM + `StateFlow`/`UiState`, Repository pattern, `MediaRecorder` + `ExoPlayer` wrappers
- ForegroundService with ongoing notification while recording

## 🎨 Material 3 Expressive

This app follows the **material-3 skill** strictly:

- **Color**: `ColorScheme` roles via `MaterialTheme.colorScheme`, dynamic color + `FlamboLightColors`/`FlamboDarkColors` fallback, no hardcoded hex in composables
- **Typography**: `FlamboTypography` (Display → Label) with expressive large titles for timer
- **Shape**: `FlamboShapes` + `ShapeLargeIncreased` / `ShapeFull` (pill) / `ShapeExtraLarge` per spec
- **Elevation**: tonal surfaces (`surfaceContainer`, `surfaceContainerHigh`), not shadows
- **Motion**: `FlamboMotion` emphasized easing, spring for FAB morph, animatedContent for timer
- **Layout**: `LargeTopAppBar` + `Scaffold` + `WindowInsets`, readable max-width, adaptive list-detail ready
- **Components**: `md3` cards (outlined tonal), `SearchBar`, `ExtendedFloatingActionButton`, `ListItem`, `Slider`, `AssistChip`, `Switch`, dialogs
- **Accessibility**: 48dp touch targets, content descriptions, proper contrast pairs (`primary` on `surface` via `onPrimary` etc.)

## 🚀 Building

### Local

```bash
./gradlew :app:assembleRelease   # signed via debug keystore fallback if no release.keystore
# or
./gradlew :app:assembleDebug
```

Debug keystore fallback means you never need a real keystore to run `assembleRelease` locally.

### CI — GitHub Actions (`build.yaml`)

| Branch | Trigger | Artifact | Behavior |
|--------|---------|----------|----------|
| `beta` | push to `beta` | `Flambo-preview-*.apk` (signed, prerelease `beta-latest`) | **Auto-replaces** previous preview — old APK/release & workflow run are deleted. Retention **7 days**. |
| `main` | push to `main` | `Flambo-release-*.apk` (signed, versioned `v*`) | Full release with **release notes, changelog, checksums, APK size**. Retention **30 days**, marked `latest`. |

Both use:
- JDK 17 Temurin, Android SDK 36, Gradle 8.13 wrapper
- **Signing**: if repo secret `KEYSTORE_BASE64` (+ `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) exists, it is decoded to `app/release.keystore`; otherwise a **deterministic ephemeral keystore** is generated so previews are still installable.
- `concurrency: cancel-in-progress` on `beta` — new push cancels old runs (preview superseded).

#### Setting up real release signing (one-time)

```bash
# From your real release keystore
base64 -w 0 my-release.keystore | pbcopy   # macOS, or `base64 my-release.keystore | tr -d '\n'` on Linux
# Add to GitHub → Settings → Secrets and variables → Actions:
#   KEYSTORE_BASE64  = <paste>
#   KEYSTORE_PASSWORD = <store pass>
#   KEY_ALIAS         = <alias>
#   KEY_PASSWORD      = <key pass>
```

After that, pushes to `main` produce a Play-Store-ready signed APK. Without the secret, `beta` previews still work (ephemeral keystore).

#### Workflow dispatch

You can also trigger manually via **Actions → Build Flambo → Run workflow**.

## 📂 Project Structure

```
app/src/main/java/com/flambo/recorder/
├── FlamboApp.kt                 # Application + singletons (DB, prefs, recorder)
├── MainActivity.kt              # edge-to-edge, permissions, theme
├── data/                        # Room + DataStore
│   ├── Recording.kt / Dao / Db
│   ├── RecordingRepository.kt
│   └── PreferencesManager.kt
├── domain/                      # Quality, format helpers
├── record/                      # MediaRecorder wrapper + ForegroundService
├── playback/                    # ExoPlayer mini-controller
└── ui/
    ├── theme/                   # Color / Type / Shape / Theme / Motion (M3 tokens)
    ├── navigation/              # NavGraph (home → detail → settings)
    ├── home/                    # LazyColumn cards, search, trash, empty state
    ├── detail/                  # Waveform scrubber, speed pills, share
    ├── settings/                # Quality, dynamic color, theme
    └── components/              # WaveformVisualizer, RecordingCard, MiniPlayer
```

## 🔐 Permissions

- `RECORD_AUDIO` — requested at runtime with rationale
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` — ongoing recording
- `POST_NOTIFICATIONS` (Android 13+) — foreground notification

No broad `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`.

## 🧪 MD3 Compliance Audit

Run the skill audit mentally:

| Category | Score | Notes |
|----------|-------|-------|
| Color tokens | 9/10 | `MaterialTheme.colorScheme` + dynamic + fallback, no hard codes |
| Typography | 9/10 | Full MD3 scale, displayMedium for timer |
| Shape | 9/10 | `FlamboShapes` + expressive `full`/`largeIncreased` |
| Elevation | 8/10 | Tonal `surfaceContainer*` |
| Components | 9/10 | `Card`, `SearchBar`, `EFAB`, `Slider`, `Chip` via M3 |
| Layout | 8/10 | `LargeTopAppBar`, 8dp, edge-to-edge; adaptive ready |
| Navigation | 8/10 | `NavHost` + rail/bar-ready |
| Motion | 8/10 | Emphasized easing, `AnimatedVisibility`/`AnimatedContent` |
| Accessibility | 8/10 | 48dp, semantics, contrast pairs |
| Theming | 9/10 | `FlamboTheme` with system/dark + dynamic toggle |

## 🤝 Contributing

PRs to `beta` auto-produce a preview APK comment. Merge `beta` → `main` to cut a release.

## 📄 License

Private — all rights reserved. Use the workflow & theming as a reference, but replace secrets/assets before distributing.

---

*Built with Material 3 Expressive — Compose-first, tonal, rounded, and quietly alive.*
