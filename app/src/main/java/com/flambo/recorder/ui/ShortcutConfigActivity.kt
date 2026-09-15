package com.flambo.recorder.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.flambo.recorder.R
import com.flambo.recorder.record.RecordingReceiver

/**
 * Lets automation apps (Key Mapper, Tasker) pick Flambo from their
 * "launch app shortcut" menu instead of hand-typing intent strings.
 * Returns a shortcut that broadcasts ACTION_TOGGLE to RecordingReceiver.
 * No UI — answers and finishes immediately.
 */
class ShortcutConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {
            // Package-scoped implicit broadcast: explicit enough for
            // background delivery, with no hard-coded class for hosts
            // (Key Mapper, Tasker) to choke on when re-firing it.
            val shortcut = Intent(RecordingReceiver.ACTION_TOGGLE).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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
