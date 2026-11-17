package com.pranshulgg.recordmaster.ui.components.tiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.ui.components.Symbol

/**
 * A row that opens another screen. The trailing chevron is what separates it
 * from an [ActionTile] that changes something in place.
 */
@Composable
fun NavigationTile(
    headline: String,
    description: String? = null,
    leading: @Composable (() -> Unit)? = null,
    shapes: RoundedCornerShape,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes,
    ) {
        ListItem(
            modifier = Modifier.clickable(
                onClick = onClick
            ),
            leadingContent = leading,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            headlineContent = { Text(headline) },
            supportingContent = {
                if (description != null) {
                    Text(description)
                }
            },
            trailingContent = {
                Symbol(
                    icon = R.drawable.chevron_right_24px,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
