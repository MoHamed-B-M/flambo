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
