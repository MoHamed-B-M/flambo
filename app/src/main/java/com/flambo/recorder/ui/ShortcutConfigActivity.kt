package com.flambo.recorder.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flambo.recorder.R
import com.flambo.recorder.record.RecordingShortcut
import com.flambo.recorder.ui.theme.FlamboTheme

/**
 * Key Mapper / Tasker "create shortcut" target. Shows a real picker of the
 * discrete recording actions (unlike the old instant toggle) and returns the
 * chosen one as a shortcut that fires it via ShortcutHandlerActivity.
 */
class ShortcutConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_CREATE_SHORTCUT) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setContent {
            FlamboTheme {
                val options = listOf(
                    RecordingShortcut.ACTION_RECORD to stringResource(R.string.shortcut_record_label),
                    RecordingShortcut.ACTION_PAUSE to stringResource(R.string.shortcut_pause_label),
                    RecordingShortcut.ACTION_STOP to stringResource(R.string.shortcut_stop_label),
                    RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE to stringResource(R.string.shortcut_toggle_rp_label),
                    RecordingShortcut.ACTION_TOGGLE_RECORD_STOP to stringResource(R.string.shortcut_toggle_rs_label)
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Text(
                            "Select the desired shortcut",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        options.forEach { (action, label) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                modifier = Modifier.clickable { returnShortcut(action, label) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun returnShortcut(action: String, label: String) {
        val shortcutIntent = Intent(this, ShortcutHandlerActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            putExtra(RecordingShortcut.EXTRA_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val result = Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
            .putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
            )
        setResult(RESULT_OK, result)
        finish()
    }
}
