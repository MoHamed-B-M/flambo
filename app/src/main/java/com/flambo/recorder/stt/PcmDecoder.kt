package com.flambo.recorder.stt

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Decodes any recorded file (AAC/M4A) into 16 kHz mono PCM, which is what
// Vosk expects. Caps length so hour-long takes can't blow the heap.
object PcmDecoder {

    const val TARGET_RATE = 16000
    private const val MAX_MINUTES = 30

    suspend fun decodeTo16kMono(path: String): ShortArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            require(track >= 0 && format != null) { "No audio track found." }
            extractor.selectTrack(track)

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val raw = decodeAll(codec, extractor)
                toMono16k(raw, channels, srcRate)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getInteger(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun decodeAll(codec: MediaCodec, extractor: MediaExtractor): ShortArray {
        val chunks = ArrayList<ShortArray>(64)
        var total = 0
        val cap = TARGET_RATE * 60 * MAX_MINUTES * 3 // raw headroom before resample
        val info = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex) ?: break
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex >= 0 -> {
                    val buf = codec.getOutputBuffer(outIndex)
                    if (buf != null && info.size > 0) {
                        val shorts = readPcm16(buf, info.offset, info.size)
                        chunks += shorts
                        total += shorts.size
                        if (total > cap) {
                            codec.releaseOutputBuffer(outIndex, false)
                            break
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) break
                else -> Unit // format change etc — keep going
            }
        }
        val out = ShortArray(total)
        var pos = 0
        for (c in chunks) {
            c.copyInto(out, pos)
            pos += c.size
        }
        return out
    }

    private fun readPcm16(buf: ByteBuffer, offset: Int, size: Int): ShortArray {
        val dup = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        dup.position(offset)
        dup.limit(offset + size)
        val n = size / 2
        return ShortArray(n) { if (dup.remaining() >= 2) dup.short else 0 }
    }

    private fun toMono16k(raw: ShortArray, channels: Int, srcRate: Int): ShortArray {
        if (raw.isEmpty()) return raw
        // Stereo -> mono by averaging.
        val mono = if (channels > 1) {
            val frames = raw.size / channels
            ShortArray(frames) { f ->
                var sum = 0
                for (ch in 0 until channels) sum += raw[f * channels + ch]
                (sum / channels).toShort()
            }
        } else raw
        if (srcRate == TARGET_RATE) return mono
        // Cheap linear resample — plenty for speech.
        val ratio = srcRate.toDouble() / TARGET_RATE
        val outLen = (mono.size / ratio).toInt().coerceAtLeast(1)
        return ShortArray(outLen) { i ->
            val pos = i * ratio
            val a = mono[pos.toInt().coerceIn(mono.indices)]
            val b = mono[(pos.toInt() + 1).coerceIn(mono.indices)]
            (a + ((b - a) * (pos - pos.toInt()))).toInt().toShort()
        }
    }
}
