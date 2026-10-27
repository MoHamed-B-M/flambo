package com.pranshulgg.recordmaster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Additive tag editor shared by the home row sheet and the playback overflow
 * menu. Tags are plain metadata (D2): they never change folders or files.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagsDialog(
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var text by rememberSaveable(initialTags) {
        mutableStateOf(initialTags.joinToString(", "))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("work, ideas, interview") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Separate tags with commas. Tags are searchable from the top bar.",
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    val tags = text.split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    onSave(tags)
                }
            ) { Text("Save", fontWeight = FontWeight.W600, fontSize = 16.sp) }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onDismiss
            ) { Text("Cancel", fontWeight = FontWeight.W600, fontSize = 16.sp) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

