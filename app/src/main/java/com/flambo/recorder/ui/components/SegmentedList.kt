package com.flambo.recorder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class SegmentedListScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun item(row: @Composable () -> Unit) { rows += row }
}

@Composable
fun segmentedListItemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
fun SegmentedList(
    modifier: Modifier = Modifier,
    content: SegmentedListScope.() -> Unit
) {
    val scope = SegmentedListScope().apply(content)
    androidx.compose.material3.Surface(
        shape = com.flambo.recorder.ui.theme.ShapeLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            scope.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
                row()
            }
        }
    }
}
