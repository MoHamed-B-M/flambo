package com.flambo.recorder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import com.flambo.recorder.update.WhatsNewItem

private val fallbackHighlights = listOf(
    WhatsNewItem("Whisper", "Multilingual offline transcription via whisper.cpp — ggml-tiny 75 MB, 95+ languages, language picker in Settings and playback sheet."),
    WhatsNewItem("Transcription engine", "Switch between Vosk (per-language) and Whisper (tiny multilingual) — fallback to Vosk English while Whisper builds."),
    WhatsNewItem("Playback fix", "Replay at end seeks to 0, progress scoped by track, debounced navigation — no stale duration or background leakage."),
    WhatsNewItem("Tachylite", "Headless Key Mapper / Tasker automation — broadcast receiver + shortcut picker + NoDisplay trampoline that never flashes the UI or queues on the lock screen."),
    WhatsNewItem("FGS fix", "Android 14+ background start no longer crashes — safely unwinds when the system denies the foreground service."),
    WhatsNewItem("Bulk actions", "Long-press to select many → Group (shared tag), Move across volumes, Delete to trash; Empty Trash with count dialog."),
    WhatsNewItem("Library layout", "List or Grid (2-column compact tiles) — toggle in Settings → Appearance."),
    WhatsNewItem("Smarter naming", "Next recording is MAX + 1 of the current prefix; empty library restarts at 1."),
    WhatsNewItem("Persistent update", "Download survives closing Settings and process death — kept until Install; auto-deletes after install."),
    WhatsNewItem("Shortcuts", "Start / Pause recording from launcher icon or hardware keys via Key Mapper."),
    WhatsNewItem("Onboarding", "First-launch tour blocks until microphone permission is granted."),
    WhatsNewItem("Settings buttons", "All 'Change' buttons use filled tonal style with animated shapes."),
    WhatsNewItem("Universal APK", "New universal APK alongside per-ABI splits — installs on any device."),
    WhatsNewItem("Recording names", "Custom prefix with auto-incrementing numbers."),
    WhatsNewItem("Export folder", "Pick any folder via the system picker, or use phone / SD-card storage."),
    WhatsNewItem("Faster startup", "No splash screen, no launch permission prompt — straight into your library."),
    WhatsNewItem("Record", "One-tap recording with live waveform, pause and resume."),
    WhatsNewItem("Enhance", "Clean audio hush and leveling, fully offline."),
    WhatsNewItem("Transcribe", "Downloadable language models, searchable transcripts."),
    WhatsNewItem("Personalize", "Theme seeds, dynamic color, storage folder choice.")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewSheet(
    version: String,
    highlights: List<WhatsNewItem>?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ShapeLargeIncreased,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "What's new",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val items = highlights?.takeIf { it.isNotEmpty() } ?: fallbackHighlights
            items.forEachIndexed { index, item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (item.title.isNotBlank()) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nice", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
