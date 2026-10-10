# Flambo — Follow-up Fixes: Duration Label, Background Notification, Key Mapper Shortcuts

Prepared for: MoHamed-B-M
Repo: https://github.com/MoHamed-B-M/flambo

Follow-up to the earlier "Playback Lifecycle, Card Tap & Search Bar" report (already fixed — not repeated here).

---

## 1 — "3 min ago" flickers to "3 min a…" during playback

### Root cause

Verified by extracting frames from `Screen_Recording_20261001_220846_Flambo.mp4`: the relative-time label truncates specifically at playback position `00:00` and `00:07` (loop start/end), staying full (`3 min ago`) everywhere in between. In `RecordingCard.kt`:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text = displayedDuration, ...)                 // no weight()
    Text("•", ...)
    Text(
        text = formatRelativeTime(recording.createdAt),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis                 // this one truncates
    )
}
```

Neither `Text` has a `weight()`, so the row's total width is only as stable as `displayedDuration`'s rendered width. In a proportional font, digits don't all have identical advance width, so the elapsed-time text's pixel width jitters slightly frame to frame. At the loop boundary this jitter is just enough to push the row over its available width for a frame or two, and the ellipsis-enabled sibling (`formatRelativeTime`) is the one that gets clipped.

### Fix

Lock the elapsed-time text to tabular (fixed-width) digits so its rendered width never changes, removing the jitter that triggers this:

```kotlin
Text(
    text = displayedDuration,
    style = MaterialTheme.typography.labelMedium.copy(
        fontFeatureSettings = "tnum"
    ),
    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
)
```

### Net effect
"3 min ago" (and any relative-time label) stays fully visible throughout playback, including at loop/end boundaries.

---

## 2 — No background-playback notification

### Root cause

Playback currently runs in a plain `ExoPlayer` with no `MediaSession`/`MediaSessionService`, so Android has no media session to attach a notification to — unlike other players (see reference screenshots), which all use `MediaSessionService`.

### Fix

This requires the `MediaSessionService` migration (new `PlaybackService.kt` + `MediaController`-based `PlaybackController`) from the earlier report. Once that's in place, Media3 automatically shows the system media notification (title, play/pause, progress) while playing in the background — no extra notification code needed. Just confirm:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
is declared in the manifest (it's already checked at runtime in `MainActivity.checkPermissions()`, just confirm the manifest entry is present too). To customize the notification's icon/title beyond the defaults, use `MediaSessionService.setMediaNotificationProvider(...)` — optional, not required for it to appear.

### Net effect
Background playback shows a standard system media notification with play/pause controls, same as other media apps.

---

## 3 — Key Mapper only sees a start/stop toggle, not discrete actions (vs. Easy Voice Recorder)

### Root cause

`RecordingShortcut.kt` only registers two **dynamic** shortcuts via `ShortcutManagerCompat.setDynamicShortcuts`, each a toggle whose *label* changes with state:

```kotlin
const val ACTION_START_RECORDING = "com.flambo.recorder.ACTION_START_RECORDING" // toggles start/stop
const val ACTION_PAUSE_RECORDING = "com.flambo.recorder.ACTION_PAUSE_RECORDING" // toggles pause/resume
```

`MainActivity.startFromExternal()` stops if already recording, starts otherwise; `pauseFromExternal()` resumes if paused, pauses otherwise. There's no standalone `Stop`, no standalone `Pause`-only, no standalone `Record`-only, and no `Toggle record/pause` (as distinct from `Toggle record/stop`). Easy Voice Recorder exposes 5 fixed, always-present actions (Record, Pause, Stop, Toggle record/pause, Toggle record/stop) as **static** shortcuts, which is why Key Mapper lists them individually and consistently regardless of app state.

### Fix

Add 5 static shortcuts (`res/xml/shortcuts.xml`, referenced from `AndroidManifest.xml`'s `<activity>` via `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />`), each pointing at a new fixed (non-relabeling) action:

```kotlin
// RecordingShortcut.kt — add alongside the existing two
const val ACTION_RECORD = "com.flambo.recorder.ACTION_RECORD"
const val ACTION_PAUSE = "com.flambo.recorder.ACTION_PAUSE"
const val ACTION_STOP = "com.flambo.recorder.ACTION_STOP"
const val ACTION_TOGGLE_RECORD_PAUSE = "com.flambo.recorder.ACTION_TOGGLE_RECORD_PAUSE"
const val ACTION_TOGGLE_RECORD_STOP = "com.flambo.recorder.ACTION_TOGGLE_RECORD_STOP"
```

```xml
<!-- res/xml/shortcuts.xml -->
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut android:shortcutId="action_record" android:icon="@mipmap/ic_launcher"
        android:shortcutShortLabel="@string/shortcut_record_label">
        <intent android:action="com.flambo.recorder.ACTION_RECORD"
            android:targetPackage="com.flambo.recorder" android:targetClass="com.flambo.recorder.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="action_pause" android:icon="@mipmap/ic_launcher"
        android:shortcutShortLabel="@string/shortcut_pause_label">
        <intent android:action="com.flambo.recorder.ACTION_PAUSE"
            android:targetPackage="com.flambo.recorder" android:targetClass="com.flambo.recorder.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="action_stop" android:icon="@mipmap/ic_launcher"
        android:shortcutShortLabel="@string/shortcut_stop_label">
        <intent android:action="com.flambo.recorder.ACTION_STOP"
            android:targetPackage="com.flambo.recorder" android:targetClass="com.flambo.recorder.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="action_toggle_record_pause" android:icon="@mipmap/ic_launcher"
        android:shortcutShortLabel="@string/shortcut_toggle_rp_label">
        <intent android:action="com.flambo.recorder.ACTION_TOGGLE_RECORD_PAUSE"
            android:targetPackage="com.flambo.recorder" android:targetClass="com.flambo.recorder.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="action_toggle_record_stop" android:icon="@mipmap/ic_launcher"
        android:shortcutShortLabel="@string/shortcut_toggle_rs_label">
        <intent android:action="com.flambo.recorder.ACTION_TOGGLE_RECORD_STOP"
            android:targetPackage="com.flambo.recorder" android:targetClass="com.flambo.recorder.MainActivity" />
    </shortcut>
</shortcuts>
```

`MainActivity.kt` — extend `handleShortcutIntent` and add discrete (non-toggle) handlers:

```kotlin
private fun handleShortcutIntent(intent: Intent?) {
    val action = intent?.action ?: return
    val known = setOf(
        RecordingShortcut.ACTION_START_RECORDING, RecordingShortcut.ACTION_PAUSE_RECORDING,
        RecordingShortcut.ACTION_RECORD, RecordingShortcut.ACTION_PAUSE,
        RecordingShortcut.ACTION_STOP, RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE,
        RecordingShortcut.ACTION_TOGGLE_RECORD_STOP
    )
    if (action !in known) return

    intent.action = null
    setIntent(intent)
    val interactive = isScreenInteractive()
    lifecycleScope.launch {
        when (action) {
            RecordingShortcut.ACTION_START_RECORDING -> startFromExternal()
            RecordingShortcut.ACTION_PAUSE_RECORDING -> pauseFromExternal()
            RecordingShortcut.ACTION_RECORD -> recordOnlyFromExternal()
            RecordingShortcut.ACTION_PAUSE -> pauseOnlyFromExternal()
            RecordingShortcut.ACTION_STOP -> stopFromExternal()
            RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE -> toggleRecordPauseFromExternal()
            RecordingShortcut.ACTION_TOGGLE_RECORD_STOP -> startFromExternal() // same semantics
        }
    }
    if (!interactive) moveTaskToBack(true)
}

// Explicit Record: no-op if already recording (vs. startFromExternal's toggle-to-stop)
private suspend fun recordOnlyFromExternal() {
    if (app.recorder.state.value.isRecording) return
    startFromExternal()
}

private suspend fun pauseOnlyFromExternal() {
    val s = app.recorder.state.value
    if (s.isRecording && !s.isPaused) app.recorder.pause()
}

private suspend fun stopFromExternal() {
    if (app.recorder.state.value.isRecording) {
        app.recorder.stop()
        RecordingShortcut.refresh(this, false, false)
    }
}

private suspend fun toggleRecordPauseFromExternal() {
    val s = app.recorder.state.value
    when {
        !s.isRecording -> recordOnlyFromExternal()
        s.isPaused -> app.recorder.resume()
        else -> app.recorder.pause()
    }
}
```

### ⚠️ Shortcut count limit — verify on device

`ShortcutManagerCompat.getMaxShortcutCountPerActivity()` is typically 4–5 depending on the launcher, and counts **dynamic + static shortcuts together** for one Activity. The existing 2 dynamic + 5 new static = 7 may exceed that on some devices. If so, the simplest fix is to drop the original 2 dynamic shortcuts (`ACTION_START_RECORDING`/`ACTION_PAUSE_RECORDING`) — `ACTION_TOGGLE_RECORD_STOP` and `ACTION_TOGGLE_RECORD_PAUSE` cover the same behavior with fixed instead of state-dependent labels.

### Net effect
Key Mapper's "Launch app shortcut" picker shows 5 discrete, always-present actions for Flambo, matching Easy Voice Recorder's behavior.
