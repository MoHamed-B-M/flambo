package com.flambo.recorder.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.flambo.recorder.R
import com.flambo.recorder.record.RecordingReceiver

class ShortcutConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {

            val shortcut = Intent(this, ShortcutHandlerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val result = Intent()
                .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut)
                .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_config_name))
                .putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
                )
            setResult(RESULT_OK, result)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
