package com.pranshulgg.recordmaster.core.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ShortBuffer

/**
 * Final-only Opus encode through the platform encoder and its Ogg muxer.
 * Ogg Opus is defined at 48 kHz, so any capture rate is resampled to 48 kHz
 * by [WavPcmReader] without touching the frozen take configuration.
 */
@RequiresApi(29)
object OpusFinalEncoder {

    private const val TARGET_RATE = 48_000
    private const val FRAMES_PER_BUFFER = 960 // 20 ms
    private const val TIMEOUT_US = 100_000L
    private const val MAX_STALLS = 100

    fun encode(source: File, target: File, bitRate: Int): Boolean {
        val reader = WavPcmReader(source, TARGET_RATE)
        val codec = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        } catch (_: Exception) {
            reader.close()
            return false
        }
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var track = -1
        try {
            val channels = reader.info.channels
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                TARGET_RATE,
                channels
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate.coerceIn(16_000, 256_000))
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            if (target.exists() && !target.delete()) return false
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)

            val shorts = ShortArray(FRAMES_PER_BUFFER * channels)
            val info = MediaCodec.BufferInfo()
            var presentationUs = 0L
            var inputDone = false
            var outputDone = false
            var stalls = 0

            while (!outputDone && stalls < MAX_STALLS) {
                var submitted = false
                var wroteOutput = false

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: return false
                        input.clear()
                        val frames = reader.readFrames(shorts, FRAMES_PER_BUFFER)
                        if (frames <= 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val values = frames * channels
                            input.put(ShortBuffer.wrap(shorts, 0, values))
                            codec.queueInputBuffer(
                                inputIndex, 0, values * 2, presentationUs, 0
                            )
                            presentationUs += frames * 1_000_000L / TARGET_RATE
                        }
                        submitted = true
                    }
                }

                while (!outputDone) {
                    when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) return false
                            // The encoder's output format carries the OpusHead
                            // codec-specific data required by the Ogg writer.
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                            wroteOutput = true
                        }

                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> wroteOutput = true

                        else -> {
                            if (outputIndex < 0) break
                            if (muxerStarted &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                                info.size > 0
                            ) {
                                val output = codec.getOutputBuffer(outputIndex)
                                if (output != null) {
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    muxer.writeSampleData(track, output, info)
                                    wroteOutput = true
                                }
                            }
                            val endOfStream =
                                info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (endOfStream) outputDone = true
                        }
                    }
                }

                stalls = if (submitted || wroteOutput) 0 else stalls + 1
            }

            if (!outputDone || !muxerStarted) return false
            muxer.stop()
            return target.length() > 0L
        } catch (_: Exception) {
            runCatching { target.delete() }
            return false
        } finally {
            runCatching { muxer?.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            reader.close()
        }
    }
}
