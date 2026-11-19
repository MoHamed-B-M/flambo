package com.pranshulgg.recordmaster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Plain-language recovery prompt. The question is asked in words rather than
 * implied by an icon or colour, and both actions are explicit — dismissing
 * keeps the staged chunks so the offer returns on the next launch.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrashRecoveryDialog(
    onRecover: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover recording?") },
        text = {
            Text("It looks like the app closed unexpectedly. Do you want to recover your last recording?")
        },
        confirmButton = {
            TextButton(
                onClick = onRecover,
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Recover", fontWeight = FontWeight.W600, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Discard", fontWeight = FontWeight.W600, fontSize = 16.sp)
            }
        }
    )
}
