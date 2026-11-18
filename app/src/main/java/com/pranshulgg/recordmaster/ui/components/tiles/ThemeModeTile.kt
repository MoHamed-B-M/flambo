package com.pranshulgg.recordmaster.ui.components.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pranshulgg.recordmaster.ui.components.FluidSegmentedButtonGroup
import com.pranshulgg.recordmaster.ui.theme.AppThemeMode

/**
 * Inline replacement for the old "App Theme" picker dialog. The segmented
 * group mirrors material-app's View/Edit/Share control: one connected row where
 * the selected segment is wider and the outer corners round off.
 */
@Composable
fun ThemeModeTile(
    headline: String,
    current: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    shapes: RoundedCornerShape,
    leading: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                leading?.invoke()
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            FluidSegmentedButtonGroup(
                options = AppThemeMode.entries,
                selected = current,
                onSelected = onSelect,
                label = { it.storageValue },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
