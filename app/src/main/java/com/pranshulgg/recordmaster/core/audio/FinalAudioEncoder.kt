package com.pranshulgg.recordmaster.core.audio

import android.os.Build
import com.pranshulgg.recordmaster.core.record.FinalAudioEncoding
import java.io.File

/** Applies the final encode selected by the frozen take plan. */
object FinalAudioEncoder {

    fun encode(
        source: File,
        target: File,
        encoding: FinalAudioEncoding,
        bitRate: Int
    ): Boolean = when (encoding) {
        FinalAudioEncoding.NONE -> false
        FinalAudioEncoding.FLAC -> FlacFinalEncoder.encode(source, target)
        FinalAudioEncoding.VORBIS -> VorbisFinalEncoder.encode(source, target)
        FinalAudioEncoding.OPUS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OpusFinalEncoder.encode(source, target, bitRate)
            } else {
                false
            }
    }
}
