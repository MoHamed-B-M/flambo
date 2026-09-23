package com.flambo.recorder.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TelegramOverlayCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SwipeToDismissContainer(
        onDismiss = onDismiss,
        modifier = modifier,
        enabled = enabled,
        background = backgroundContent ?: { },
        content = content
    )
}
