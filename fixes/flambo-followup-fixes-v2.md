# Flambo — Follow-up Fixes, Round 2 (reviewed against v1.7.1-11 source)

Prepared for: MoHamed-B-M
Repo: https://github.com/MoHamed-B-M/flambo
Based on: `flambo-1_7_1-11.zip` + Issue #3 comments from Zokii0

All three items from the previous round were attempted but aren't fully resolved yet. Below is the exact gap in each, found by reading the actual current source.

---

## 1 — "3 min ago" still truncates ("Still flickers")

### What's in place vs. what's missing

`RecordingCard.kt`, lines 184–217:

```kotlin
Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = recording.title,
            ...
            modifier = Modifier.weight(1f, fill = false)   // ← this weight is on the TITLE row
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = displayedDuration,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), // ✅ applied
            ...
        )
        Text("•", ...)
        Text(
            text = formatRelativeTime(recording.createdAt),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis   // ← still no weight() here
        )
    }
    ...
}
```

The `tnum` half of the fix is in place. The `weight(1f, fill = false)` half ended up on the **title** Text (a different, unrelated row) instead of on the `formatRelativeTime` Text in the duration row. That row still has no width allocation between its two Texts, so it's exactly as unstable as before — confirmed on `Screen_Recording_20261006_232655_Flambo.mp4`, where `"10 min ago"` is truncated to `"10 min a…"` for nearly the entire clip (not just at loop boundaries — a longer base string like "10 min ago" makes this worse than the "3 min ago" case tested earlier).

### Fix — move the weight to the correct Text

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = displayedDuration,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false
    )
    Text("•", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    Text(
        text = formatRelativeTime(recording.createdAt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)   // ← add this here instead
    )
}
```

Leave the title row's existing `weight(1f, fill = false)` exactly as it is — that one is fine and unrelated.

### Net effect
Duration never shrinks; the relative-time label only shrinks if genuinely out of room, and does so cleanly instead of flickering.

---

## 2 — No status bar icon for the background playback notification

### Root cause

`PlaybackService.kt` builds the `MediaSession` correctly but never calls `setMediaNotificationProvider(...)` or supplies an explicit small icon, so Media3 falls back to a default that doesn't render as a proper status bar icon — Android requires the **status bar (small) icon** to be a monochrome (white-on-transparent) image; a full-color launcher mipmap like `R.mipmap.ic_launcher` is accepted by the API but typically renders blank or invisible in the collapsed status bar, especially on Samsung One UI. This matches exactly what you're seeing: fine in the expanded shade (which uses the large icon/content), invisible as the small icon next to the clock.

### Fix

**1. Add a monochrome vector drawable** — `res/drawable/ic_notification.xml`, single white fill, mostly transparent (reuse the mic glyph silhouette, no color/background):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#FFFFFFFF">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92h-2z" />
</vector>
```
(swap the path for whatever mic glyph the app already uses elsewhere — the key requirement is single-color fill, no gradient/multi-color.)

**2. Wire it into the media notification** in `PlaybackService.kt`:

```kotlin
import androidx.media3.session.DefaultMediaNotificationProvider
import com.flambo.recorder.R

override fun onCreate() {
    super.onCreate()
    setMediaNotificationProvider(
        DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("flambo_playback")
            .setChannelName(R.string.playback_channel_name) // add this string if missing
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
    )
    val player = ExoPlayer.Builder(this)
        ...
}
```

(The exact builder/setter shape depends on your Media3 version — `DefaultMediaNotificationProvider` in some versions takes the icon in its constructor instead of a separate `setSmallIcon` call; adjust to whatever your `media3-session` version exposes. The important part is: explicit provider + explicit monochrome icon, not the default fallback.)

### Net effect
A proper small icon appears next to the clock while playing in the background, matching Brave/other media apps.

---

## 3 — Key Mapper's "Launch app shortcut" doesn't show a picker

### Root cause

`ShortcutConfigActivity.kt`:

```kotlin
class ShortcutConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {
            val shortcut = Intent(this, ShortcutHandlerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val result = Intent()
                .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut)
                .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_config_name))
                .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, ...)
            setResult(RESULT_OK, result)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
```

This is declared with `android:theme="@android:style/Theme.NoDisplay"` and returns a single, hardcoded shortcut (`"Toggle Recording"`, which calls `toggleHeadless()`) the instant it's launched — there is no UI by design, so there's nothing for Key Mapper to show the user a choice from. This is Android's `ACTION_CREATE_SHORTCUT` picker mechanism (the same one Key Mapper's "Launch app shortcut" action uses) — separate from the 5 static `ShortcutManager` shortcuts added earlier (those power the launcher's long-press menu, a different system, and are already working per the "What's new" sheet).

### Fix

**1. Give `ShortcutConfigActivity` a real (visible) picker UI** instead of `Theme.NoDisplay`, listing the same 5 actions, and pass the chosen action through to the handler:

```kotlin
// RecordingShortcut.kt — add this constant
const val EXTRA_ACTION = "com.flambo.recorder.EXTRA_SHORTCUT_ACTION"
```

```kotlin
// ShortcutConfigActivity.kt
class ShortcutConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_CREATE_SHORTCUT) {
            setResult(RESULT_CANCELED); finish(); return
        }
        setContent {
            FlamboTheme {
                val options = listOf(
                    RecordingShortcut.ACTION_RECORD to stringResource(R.string.shortcut_record_label),
                    RecordingShortcut.ACTION_PAUSE to stringResource(R.string.shortcut_pause_label),
                    RecordingShortcut.ACTION_STOP to stringResource(R.string.shortcut_stop_label),
                    RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE to stringResource(R.string.shortcut_toggle_rp_label),
                    RecordingShortcut.ACTION_TOGGLE_RECORD_STOP to stringResource(R.string.shortcut_toggle_rs_label),
                )
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding()) {
                        Text(
                            "Select the desired shortcut",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        options.forEach { (action, label) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                modifier = Modifier.clickable { returnShortcut(action, label) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun returnShortcut(action: String, label: String) {
        val shortcutIntent = Intent(this, ShortcutHandlerActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            putExtra(RecordingShortcut.EXTRA_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val result = Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
            .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher))
        setResult(RESULT_OK, result)
        finish()
    }
}
```

**2. Make `ShortcutHandlerActivity` act on the chosen action** instead of always calling `toggleHeadless()` (the `RecordingController` API already has exactly the right guarded methods — `startHeadless()`, `pause()`, `resume()`, `stop()`, `toggleHeadless()` — no changes needed there):

```kotlin
class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlamboApp
        val action = intent?.getStringExtra(RecordingShortcut.EXTRA_ACTION)
            ?: RecordingShortcut.ACTION_TOGGLE_RECORD_STOP // back-compat for any old pinned shortcut

        app.appScope.launch {
            try {
                val recorder = app.recorder
                when (action) {
                    RecordingShortcut.ACTION_RECORD -> recorder.startHeadless()
                    RecordingShortcut.ACTION_PAUSE -> recorder.pause()
                    RecordingShortcut.ACTION_STOP -> recorder.stop()
                    RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE -> {
                        val s = recorder.state.value
                        when {
                            !s.isRecording -> recorder.startHeadless()
                            s.isPaused -> recorder.resume()
                            else -> recorder.pause()
                        }
                    }
                    RecordingShortcut.ACTION_TOGGLE_RECORD_STOP -> recorder.toggleHeadless()
                }
            } catch (_: Exception) {
            } finally {
                val s = app.recorder.state.value
                runCatching { RecordingShortcut.refresh(app, s.isRecording, s.isPaused) }
            }
        }
        finishAndRemoveTask()
    }
}
```

**3. `AndroidManifest.xml`** — drop the `NoDisplay` theme on `ShortcutConfigActivity` so it can actually show the picker:

```xml
<activity
    android:name=".ui.ShortcutConfigActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:theme="@style/Theme.Flambo">
    <intent-filter>
        <action android:name="android.intent.action.CREATE_SHORTCUT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```
(`ShortcutHandlerActivity` stays exactly as it was — `NoDisplay`/`noHistory`/`taskAffinity=""` — it's still the invisible trampoline that actually performs the action once a shortcut is tapped.)

### Net effect
Picking "Launch app shortcut" for Flambo in Key Mapper now shows a real list (Record / Pause / Stop / Toggle record-pause / Toggle record-stop), matching Easy Voice Recorder's behavior, and creates a shortcut that runs exactly that one discrete action.
