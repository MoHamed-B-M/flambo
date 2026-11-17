package com.pranshulgg.recordmaster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranshulgg.recordmaster.ui.expressive.HoldToConfirmButton
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "OK",
    cancelText: String = "Cancel",
    holdToConfirm: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (holdToConfirm) {
                // Destructive actions need deliberate friction: the dialog
                // explains the consequence, the hold makes it hard to do by accident.
                HoldToConfirmButton(
                    text = "Hold to ${confirmText.lowercase(Locale.getDefault())}",
                    onHoldComplete = {
                        onConfirm()
                        onDismiss()
                    },
                    height = 48.dp,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(confirmText, fontWeight = FontWeight.W600, fontSize = 16.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(cancelText, fontWeight = FontWeight.W600, fontSize = 16.sp)
            }
        }
    )
}
