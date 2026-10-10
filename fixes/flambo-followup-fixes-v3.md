# Flambo — Follow-up Fixes, Round 3 (reviewed against flambo-beta-latest source)

Prepared for: MoHamed-B-M
Repo: https://github.com/MoHamed-B-M/flambo

Status of the 3 items from Round 2, plus one newly surfaced issue.

---

## ✅ Key Mapper shortcut picker — confirmed fixed, no action needed

`ShortcutConfigActivity.kt` now shows the picker (Record / Pause / Stop / Toggle record-pause / Toggle record-stop), confirmed working via a Key Mapper screen recording binding it to Volume Down. Nothing further needed here.

---

## New — mic privacy indicator (green dot) stays on during Pause (not Stop)

### Root cause

`RecordingController.kt`:

```kotlin
fun pause() {
    val s = _state.value
    if (!s.isRecording || s.isPaused) return
    if (s.source == AudioSource.SYSTEM) { ... }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try { recorder?.pause() } catch (_: Exception) { return }   // ← MediaRecorder.pause()
        ...
    }
}
```

`MediaRecorder.pause()` is documented Android platform behavior: it stops writing samples but **does not release the underlying audio input session** — the mic stays "in use" from the OS's point of view, so the privacy indicator (green dot) stays lit. Only `stop()` + `release()` actually releases it. This is why Stop clears the dot and Pause doesn't — not a Flambo-specific bug, but a consequence of using `MediaRecorder.pause()/resume()` for the pause feature. Easy Voice Recorder's pause almost certainly works differently: stop + release the recorder on pause (dot disappears), then open a new recording segment on resume and stitch segments together into one file at the end.

### Fix — scope and approach (needs design discussion, not a one-line patch)

Replacing `MediaRecorder.pause()/resume()` with a real stop+release+reopen cycle is an architecture-level change:

1. On `pause()`: call the existing `stop()` path to finalize and save the current segment (releases the mic, clears the privacy dot), but mark it as a "paused" segment rather than a finished recording.
2. On `resume()`: start a new `MediaRecorder` segment into a new temp file.
3. On the real `stop()` (recording finished): if there are multiple segments, concatenate them into the final output file before returning the `Recording`.

The exact concatenation approach depends on the recording format Flambo uses (PCM/WAV can be concatenated directly at the sample level; AAC/M4A needs either remuxing or re-encoding). This needs a look at `AudioFileStore.kt` / the encoder setup to pick the least invasive approach — happy to design this in detail with those files if useful, but it's a real feature change, not a quick fix, so flagging it as such rather than guessing at the full implementation blind.

---

## 1 — Duration/relative-time flicker: still occurs at loop boundaries despite correct fix

### What's confirmed

`RecordingCard.kt` now has the exact fix from Round 2 — `tnum` on the duration Text, `weight(1f, fill = false)` correctly on the `formatRelativeTime` Text. Verified in source. Despite that, `Screen_Recording_20261007_121919_Flambo.mp4` still shows `"1 min a…"` truncated specifically at `00:00` and near the end (`00:06`–`00:08`), staying full (`"1 min ago"`) for `00:01`–`00:05`.

### Why the previous fix wasn't quite enough

`Modifier.weight(1f, fill = false)` resolves its sibling's available space in a two-pass measurement. At the exact loop-restart moment, position resets to 0 and several pieces of state can change in the same recomposition window — this timing can produce a stale measurement for a frame or two, even with `tnum` locking digit widths. The remaining flicker is a measurement-timing artifact, not a font-width issue anymore.

### Stronger fix — reserve a fixed width up front instead of measuring dynamically every frame

```kotlin
val textMeasurer = rememberTextMeasurer()
val durationStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
val maxDurationWidthPx = remember(recording.durationMs) {
    val total = formatDuration(recording.durationMs)
    textMeasurer.measure(text = "$total / $total", style = durationStyle).size.width
}
val density = LocalDensity.current

Text(
    text = displayedDuration,
    style = durationStyle,
    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    softWrap = false,
    modifier = Modifier.width(with(density) { maxDurationWidthPx.toDp() })
)
```

This measures the worst-case width (`"total / total"`, which is always ≥ any `"position / total"` value for this recording) once per recording and locks the duration Text to that exact width via a hard `Modifier.width(...)`, so the Row's space allocation for the sibling `weight(1f, fill = false)` Text becomes fully deterministic — immune to any two-pass measurement timing race, regardless of how many state changes land in the same recomposition.

### Net effect
No flicker at any point during playback, including loop/end boundaries, because the layout no longer depends on per-frame measurement order at all.

---

## 2 — Status bar / Live Notification icon: code is correct, still not visible — likely not a code issue

### What's confirmed

`PlaybackService.kt`, the manifest permissions/service declaration, and `res/drawable/ic_notification.xml` all match the recommended, verified-working pattern exactly (same `DefaultMediaNotificationProvider.Builder(...).build().apply { setSmallIcon(...) }` shape, same monochrome single-path vector). Yet `Screen_Recording_20261007_121919_Flambo.mp4`'s status bar, captured while a recording is actively playing in Flambo, shows no Flambo icon at all (only screen-record, screenshot, a DND-related icon, and WhatsApp).

### Ruled out: this is not a hidden per-app Samsung setting

Checked Settings → Notifications → "Obaveštenja uživo" (Live Notifications) on the actual device. The "Plejer medija" (Media player) entry there is a **generic master toggle covering every app** that posts a standard Android media-style notification (Spotify, YouTube Music, etc. all share this one entry — apps aren't listed individually for this category) — and it's already **on**. So there's no missing per-app toggle to flip for Flambo; this theory from the previous note is ruled out.

Since that master toggle is already on and Flambo still isn't getting the status bar icon / live treatment, the system most likely isn't recognizing Flambo's posted notification as a valid media-session notification at all — pointing back to a code/technical cause:

### Try in this order

**1. Bump the `media3-session` version — concrete code change, do this first:**
```gradle
implementation "androidx.media3:media3-session:1.8.0"  // or whatever the current latest stable is
```
This directly targets a confirmed Media3 bug (`androidx/media` issue tracker, reproduced on media3-session 1.6.0): if anything triggers internal `MediaNotificationManager` creation before `setMediaNotificationProvider(...)` runs, the custom provider — and the custom icon — gets silently dropped in favor of the default. Flambo's current code calls `setMediaNotificationProvider` right after `super.onCreate()` with nothing in between, which matches official guidance and shouldn't normally trigger this, but it's worth ruling out directly since it's an exact match for the symptom. If the project is on 1.6.x or similar, this alone may be the entire fix.

**2. Check the device's One UI patch level** (Settings → About phone → Software information) — zero code change, just a fact about the test device. Samsung only shipped proper Live Notification support for third-party media-style notifications starting with specific One UI 7 builds (reports point to builds from around the April 2025 security patch onward); earlier builds silently don't promote any app's media notification to the status bar icon / pill, no matter how correct the code is. If the device is on an older build, there's nothing to fix in Flambo — it'll just start working after a system update.

**3. If neither explains it**, run `adb shell dumpsys notification` while a recording is playing to see exactly what Android received from Flambo's notification (template/category). This shows definitively whether it's even being classified as `MediaStyle`, which would point to whatever is actually left to fix in code.
