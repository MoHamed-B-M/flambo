package com.flambo.recorder.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flambo.recorder.ui.theme.ShapeLargeIncreased

// M3 expressive segmented list: one rounded tonal container, rows split by
// hairline dividers. Rows should use [segmentedListItemColors] (transparent)
// so the group background shows through with no shadow anywhere.
class SegmentedListScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun item(row: @Composable () -> Unit) {
        rows += row
    }
}

@Composable
fun segmentedListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
fun SegmentedList(
    modifier: Modifier = Modifier,
    content: SegmentedListScope.() -> Unit
) {
    val scope = SegmentedListScope().apply(content)
    Surface(
        shape = ShapeLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            scope.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                row()
            }
        }
    }
}
