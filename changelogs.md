# Changelog

## [1.6.5] - 2026-09-27

Maintenance update focused on card stability, storage sync and recording-button polish. No migrations — just update.

### What's new

- **Scroll-adaptive Record button** — Big 76dp pill with Mic + "Record" at the top of the library; collapses to an icon-only button in the bottom-right once you scroll down. Same action in both states, with a soft spring morph and staggered label.
- **Card actions in one overflow menu** — Open details, Favorite, Rename and Delete now live under the 3-dot menu as full-width buttons in primary, tertiary, tonal and error colors, leaving Play/Pause, the waveform and the menu on the card row.
- **Scrub inside Playback too** — The detail screen's waveform now seeks with the same tap/drag gesture as the cards.

### Improvements

- **Waveform fills the card** — The progress bar takes the full available width with tap/drag scrubbing across the whole bar; scrubbing a resting card starts it and applies the position once prepared.
- **Smarter card tap** — Tapping a card toggles selection only; a dedicated Open action navigates to Playback, so scrubbing never misfires into the detail screen.
- **Compact title row** — Title is single-line ellipsis at `bodyMedium` with duration and date on one compact metadata row, fixing "Reco..." truncation and horizontal clipping.
- **Real-time storage sync** — A debounced watcher on the library dir plus launch/resume re-scans adopts audio restored or copied in from outside (app dir and custom SAF tree, in-progress takes excluded, durations read).
- **Trash navigation** — Back gesture exits Trash to the library, the top icon becomes a back arrow, and the "Moved to trash - Undo" snackbar is dismissed on Trash entry and on permanent deletes.
- **Pinned header** — The app bar stays on top while scrolling instead of ducking away.
- **Expand-on-play cards** — Pressing play always reveals a progress bar: the waveform when peaks exist, a seekable linear track for peak-less files (e.g. restored audio).

### Fixes

- **External-delete guard** — Playback checks the file every ~2s; a file deleted in a file manager stops playback immediately, releases the player and marks the card missing.
- **Release-build stability** — Fixed the Record-button animation's `AnimatedContent` transition (`togetherWith` `ContentTransform`) that broke `compileReleaseKotlin`.
- **Missing-file flags refresh** — Cards recompute missing state after syncs and playback errors without flicker.
- **Waveform measured correctly** — The card progress bar uses full width with a fixed height instead of a conflicting vertical weight, so it can no longer collapse to zero.

### Updating from 1.6.0

Just update — no extra steps. Your layout, icon and progress bar choices carry over.

---

## [1.6.0] - 2026-09-26

A playback and motion update. Transport controls become one connected group, and cards open like apps.

### What's new

- **Card expand animation** — Grid and list cards zoom open into Playback and minimize back, like opening an Android app. Toggle it in Appearance → Layout.
- **Expressive transport controls** — Skip back, play/pause and skip forward are now one connected button group with an animated play/pause icon.
- **No-slide mode** — While the card animation is on, the old sliding transitions step aside so nothing fights the morph. Turn it off to get the classic slides back.
- **Recording-focus mode** — While recording, the search bar and library step aside so only the recording card stays visible.
- **Groups** — Long-press recordings, tap Group, and give it a name. A folder button appears in the top bar once your first group exists; open it to browse folders, tap one to see its recordings, and pick each folder's color. Swipe a folder away to delete the group (recordings stay, Undo brings it back).
- **Bigger recording card** — The recording card fills the screen while recording with slimmer controls that adapt to short screens so nothing overflows.
- **Brand intro** — A short Flambo animation plays on every launch. Turn it off in Appearance if you'd rather skip straight to your recordings.
- **Brand themes** — Color schemes are now Nothing, OnePlus, Apple and GitHub presets plus Dynamic wallpaper colors, each with matching Light and Dark modes.
- **App theme, one place** — Brightness and palettes live in a single App theme dialog; cards lift off dark backgrounds so they stay easy to see.

### Improvements

- Transport buttons have breathing room and shrink slightly when pressed.
- Forward skip is now 5 seconds, matching rewind.
- Cleaned playback shows a single progress bar following your Slider, Linear or Wavy setting.
- The card morph runs slower and step-by-step, with no fades fighting it; swipe-to-dismiss parks while the morph is on.
- The search bar is back to default Material colors.
- What's New now pops after the intro finishes instead of underneath it.
- The header, search bar and Record button duck while scrolling and slide back 2s after you stop.
- Dynamic color now applies to every palette style, not just Dynamic.
- Fixed an occasional crash when swiping back from playback.
- Custom theme parked for now; the five-brand lineup is Dynamic, Nothing, OnePlus, Apple and GitHub.

### Demos

<video controls width="270">
  <source src="https://raw.githubusercontent.com/MoHamed-B-M/flambo/beta/screenshots/demos/Record_2026-09-26-20-42-25.mp4" type="video/mp4">
  <a href="https://github.com/MoHamed-B-M/flambo/blob/beta/screenshots/demos/Record_2026-09-26-20-42-25.mp4">Watch demo 1 on GitHub</a>
</video>

<video controls width="270">
  <source src="https://raw.githubusercontent.com/MoHamed-B-M/flambo/beta/screenshots/demos/Record_2026-09-26-20-42-50.mp4" type="video/mp4">
  <a href="https://github.com/MoHamed-B-M/flambo/blob/beta/screenshots/demos/Record_2026-09-26-20-42-50.mp4">Watch demo 2 on GitHub</a>
</video>

<video controls width="270">
  <source src="https://raw.githubusercontent.com/MoHamed-B-M/flambo/beta/screenshots/demos/VID_20260926205046.mp4" type="video/mp4">
  <a href="https://github.com/MoHamed-B-M/flambo/blob/beta/screenshots/demos/VID_20260926205046.mp4">Watch demo 3 on GitHub</a>
</video>

<video controls width="270">
  <source src="https://raw.githubusercontent.com/MoHamed-B-M/flambo/beta/screenshots/demos/Record_2026-09-26-22-20-10.mp4" type="video/mp4">
  <a href="https://github.com/MoHamed-B-M/flambo/blob/beta/screenshots/demos/Record_2026-09-26-22-20-10.mp4">Watch demo 4 on GitHub</a>
</video>

---

### Updating from 1.5.0

Just update — no extra steps. Your layout, icon and progress bar choices carry over.
