package com.pranshulgg.recordmaster.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins the MPEG-4/AAC chunks produced by a paused-and-resumed take into one
 * file. Encoded samples are copied (never re-encoded), so quality is preserved
 * and no additional audio generation is needed.
 *
 * The AAC encoder adds a small amount of priming at each chunk boundary; with
 * identical encoder settings across chunks the result is effectively seamless
 * for voice recordings.
 */
object AudioChunkMerger {

    data class Chunk(val file: File, val durationMs: Long)

    /**
     * MediaExtractor and MediaCodec use different flag constants. Translate the
     * extractor's sample flags into BufferInfo flags so lint (and the muxer) see
     * valid values instead of raw SAMPLE_FLAG_* bits.
     */
    private fun bufferFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    fun merge(chunks: List<Chunk>, output: File): Boolean {
        val valid = chunks.filter { it.file.exists() && it.file.length() > 0L }
        if (valid.isEmpty()) return false

        if (valid.size == 1) {
            return try {
                if (output.exists() && !output.delete()) return false
                valid.first().file.copyTo(output, overwrite = true)
                output.length() > 0L
            } catch (_: Exception) {
                runCatching { output.delete() }
                false
            }
        }

        return try {
            if (output.exists()) output.delete()
            val muxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            var trackIndex = -1
            var timeOffsetUs = 0L
            val buffer = ByteBuffer.allocateDirect(256 * 1024)

            try {
                for (chunk in valid) {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(chunk.file.absolutePath)
                        val track = (0 until extractor.trackCount).firstOrNull { index ->
                            extractor.getTrackFormat(index)
                                .getString(MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") == true
                        } ?: continue

                        val format = extractor.getTrackFormat(track)
                        extractor.selectTrack(track)
                        if (trackIndex < 0) {
                            trackIndex = muxer.addTrack(format)
                            muxer.start()
                        }

                        while (true) {
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size <= 0) break
                            val info = MediaCodec.BufferInfo().apply {
                                set(
                                    0,
                                    size,
                                    extractor.sampleTime + timeOffsetUs,
                                    bufferFlags(extractor.sampleFlags)
                                )
                            }
                            muxer.writeSampleData(trackIndex, buffer, info)
                            extractor.advance()
                        }
                        val encodedDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            runCatching { format.getLong(MediaFormat.KEY_DURATION) }
                                .getOrDefault(0L)
                        } else {
                            0L
                        }
                        timeOffsetUs += encodedDurationUs.takeIf { it > 0L }
                            ?: chunk.durationMs.coerceAtLeast(0L) * 1_000L
                    } finally {
                        extractor.release()
                    }
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
            output.length() > 0L
        } catch (_: Exception) {
            runCatching { output.delete() }
            false
        }
    }
}
