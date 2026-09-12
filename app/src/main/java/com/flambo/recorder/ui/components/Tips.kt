package com.flambo.recorder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flambo.recorder.ui.theme.ShapeLargeIncreased

data class Tip(val title: String, val body: String)

// Short how-tos, one per card. Kept plain and factual — each must stay true.
val AppTips = listOf(
    Tip(
        "Pause without stopping",
        "Use Pause during a take to skip the boring parts. Resume keeps one continuous timeline."
    ),
    Tip(
        "Find words, not files",
        "Search looks through titles, tags and even transcripts — not just file names."
    ),
    Tip(
        "Clean up recordings",
        "Open a recording and tap Clean audio. Pick Light, Balanced or Strong in Settings, and keep or replace the original."
    ),
    Tip(
        "Transcribe offline",
        "Download a language model once in Settings, then turn any recording into text with no internet."
    ),
    Tip(
        "Share anything",
        "Send the original or the cleaned copy straight from the recording screen."
    ),
    Tip(
        "Pick where recordings live",
        "Settings → Storage folder moves new recordings to internal storage, phone storage or SD card."
    ),
    Tip(
        "Stay updated",
        "Settings → Updates follows beta or stable builds and can install them in-app."
    )
)

@Composable
fun TipCard(
    tip: Tip,
    position: String,
    onNext: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ShapeLargeIncreased,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    tip.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    position,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Text(
                tip.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onHide) {
                    Text("Hide tips", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                TextButton(onClick = onNext) {
                    Text("Next tip", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}
