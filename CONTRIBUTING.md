# Contributing to Flambo

Thank you for considering Flambo! This document helps you get started quickly — from building locally to opening a clean PR against `beta`.

> **Design vibe:** Flambo is a **Material 3 Expressive** showcase — tonal surfaces over shadows, morphing shapes, bouncy springs (`DampingRatioMediumBouncy`, `StiffnessMediumLow`), and dynamic color. Please keep new UI in that language.

---

## Quick start

```bash
git clone https://github.com/MoHamed-B-M/flambo.git
cd flambo
git checkout beta          # active development lives here
./gradlew :app:assembleRelease
```

Per-ABI APKs land in `app/build/outputs/apk/release/` (`arm64` + `armv7`, no universal). A debug-keystore fallback means the command works with **no secrets** configured.

Requirements: **JDK 21**, **AGP 9.3.2**, **Kotlin 2.3.10**, Android SDK `compileSdk 37 / targetSdk 36 / minSdk 26` (`platforms;android-37` comes from the canary channel or the preinstalled runner image — it is not on the stable sdkmanager channel).

---

## Branches & releases

| Branch | What it is | CI does |
|---|---|---|
| `beta` | Rolling preview `1.0.2-dev(#N)` (`100000 + runNumber` versionCode so stable→beta is always an update) | Builds, signs, publishes to `beta-latest` prerelease (previous asset wiped, one build always), artifact 7 days |
| `main` | Versioned stable `x.y.z+BUILD` (pins `BASE_VERSION`/`STABLE_BUILD`) | Builds, signs, creates `vX.Y.Z+BUILD` release with notes + checksums, artifact 30 days, marks latest |

- **Single source of truth:** `BASE_VERSION`, `STABLE_BUILD`, `BETA_CODE_OFFSET` at the top of `.github/workflows/build.yaml`. Bump `BASE_VERSION` for a stable, beta follows automatically (`1.0.2-dev(#N)`).
- Docs-only pushes (`README.md`, `screenshots/**`) skip CI via `paths-ignore`.
- Never commit a keystore — CI decodes `KEYSTORE_BASE64` → `app/release.keystore` at build time, falls back to ephemeral/debug otherwise.

---

## Project map

```
app/src/main/java/com/flambo/recorder/
├── ui/
│   ├── home/          # Library, search, recording panel, mini-player
│   ├── detail/        # Playback, waveform scrubber, transcribe sheet, transcript card
│   ├── settings/      # Segmented preference groups, Vosk model manager, updates, about
│   ├── onboarding/    # First-launch expressive pager tour
│   ├── intro/         # 3s zoom+fade splash + intro sound (every cold start)
│   ├── components/    # Waveform, cards, SegmentedList, mini-player
│   └── theme/         # M3 Expressive color/type/shape/motion tokens
├── record/            # RecordingController (MediaRecorder / SystemAudioEngine), service
├── audio/             # Offline enhancer
├── stt/               # Vosk offline transcription
├── playback/          # ExoPlayer controller
├── update/            # UpdateChecker (pop sound on available) + ApkInstaller
└── data/              # Room, Repository, DataStore prefs, SafFolderHelper, SoundPlayer
app/src/main/res/
├── raw/pop.mp3, intro_sound.mp3
├── drawable/intro.xml
└── mipmap/.../ic_launcher.png
```

MVVM: `ViewModel` + `StateFlow`, repository pattern, thin platform wrappers.

---

## How to contribute

1. **Fork** and `git checkout -b feat/your-thing` from `beta`.
2. Keep changes **focused** — one feature/fix per PR.
3. Run the app on a real device (emulator lacks `RECORD_AUDIO` + Vosk native libs nuance).
4. Ensure `./gradlew :app:assembleRelease` still passes (CI does this).
5. Open a PR **against `beta`**, not `main`. Describe screenshots/gifs for UI changes.

### Commit style

Use short imperative prefixes: `feat:`, `fix:`, `ci:`, `docs:`, `chore:`, `refactor:`.

```
feat: custom recording prefix Sound 1, 2, …
fix: SAF copy not persisting after reboot
ci: bump BASE_VERSION to 1.0.3
```

### Code style

- **Kotlin** + **Compose**. Prefer `collectAsState`, `remember`, `LaunchedEffect`, `spring(...)`.
- **Material 3 Expressive:** use `ShapeLargeIncreased`, `ShapeFull`, `surfaceContainer*` tonal colors, `ButtonDefaults.shapes()`, `ToggleButton` + `ButtonGroupDefaults.connected*ButtonShapes()` for segmented groups, `LinearWavyProgressIndicator` for progress.
- No shadows on cards — tonal elevation only. Motion: `Spring.DampingRatioMediumBouncy` / `StiffnessMediumLow`.
- Keep `PreferencesManager` as the single DataStore surface; add a new `Keys` entry + `Flow` + `set*` for each setting. Mirror volatile cache in `FlamboApp` if read on the hot path (e.g. `recordingsDir`, `recordingPrefix`).
- Naming: recording titles are `"$prefix $n"` via `RECORDING_NEXT_NUMBER`. Don't hand-roll timestamps.

### Adding a setting

1. Add key in `PreferencesManager.Keys` + `Flow` + `suspend set*`.
2. If needed, cache in `FlamboApp` (`@Volatile var` + preload in `onCreate`).
3. Add row in `SettingsScreen` inside the appropriate `SegmentedList` (see Recording / Sound / Appearance groups). Follow the existing `ListItem` + `Switch` / `ToggleButton` pattern; add an `AlertDialog` with `ShapeLargeIncreased` for selection.
4. For folder picks, use `rememberLauncherForActivityResult(OpenDocumentTree)` + `takePersistableUriPermission` + `SafFolderHelper`.

### Sounds & intro

- `res/raw/pop.mp3` plays via `SoundPlayer.playPop()` only when `UpdateChecker.available == true` (posted to main thread).
- `res/raw/intro_sound.mp3` plays together with `IntroScreen` (zoom 0.6→1 + fade) for 3 s on every cold start. To change duration, edit `IntroScreen(durationMs)`.

---

## Reporting bugs & ideas

- Check [existing issues](https://github.com/MoHamed-B-M/flambo/issues) first.
- Include: Flambo version (`vX.Y.Z+BUILD` or `1.0.x-dev(#N)`), Android version, device, steps, expected vs actual, logcat if relevant.
- For update/install issues, note the channel (beta/stable) and the SHA-256 from the release.

---

## Questions?

Open a discussion or ping [@MoHamed-B-M](https://github.com/MoHamed-B-M). Contributions — code, docs, screenshots, translations, or just testing the beta — are all welcome.
