package com.pranshulgg.recordmaster.core.stt

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmDecoder {

    const val TARGET_RATE = 16000
    private const val MAX_MINUTES = 30

    data class PcmAudio(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int
    ) {
        val frames: Int get() = if (channels <= 0) 0 else samples.size / channels
    }

    suspend fun decodeNative(path: String, maxMinutes: Int = MAX_MINUTES): PcmAudio =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val candidate = extractor.getTrackFormat(i)
                    if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        track = i
                        format = candidate
                        break
                    }
                }
                require(track >= 0) { "No audio track found." }
                val audioFormat = format ?: error("No audio track found.")
                extractor.selectTrack(track)

                val sourceRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"

                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(audioFormat, null, null, 0)
                    codec.start()
                    val cap = (sourceRate * 60L * maxMinutes * channels.coerceAtLeast(1))
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                    val raw = decodeAll(codec, extractor, cap)

                    if (raw.size > cap) error("Audio is longer than $maxMinutes minutes.")
                    PcmAudio(raw, sourceRate, channels)
                } finally {
                    runCatching { codec.stop() }
                    runCatching { codec.release() }
                }
            } finally {
                runCatching { extractor.release() }
            }
        }

    suspend fun decodeTo16kMono(path: String): ShortArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = candidate
                    break
                }
            }
            require(track >= 0) { "No audio track found." }
            val audioFormat = format ?: error("No audio track found.")
            extractor.selectTrack(track)

            val sourceRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(audioFormat, null, null, 0)
                codec.start()
                val raw = decodeAll(codec, extractor, TARGET_RATE * 60 * MAX_MINUTES * 3)
                toMono16k(raw, channels, sourceRate)
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

    private fun decodeAll(
        codec: MediaCodec,
        extractor: MediaExtractor,
        capSamples: Int
    ): ShortArray {
        val chunks = ArrayList<ShortArray>(64)
        var total = 0

        val info = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex) ?: break
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        val shorts = readPcm16(buffer, info.offset, info.size)
                        chunks += shorts
                        total += shorts.size
                        if (total > capSamples) {
                            codec.releaseOutputBuffer(outputIndex, false)
                            break
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) break
                else -> Unit
            }
        }
        val out = ShortArray(total)
        var position = 0
        for (chunk in chunks) {
            chunk.copyInto(out, position)
            position += chunk.size
        }
        return out
    }

    private fun readPcm16(buffer: ByteBuffer, offset: Int, size: Int): ShortArray {
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(offset)
        duplicate.limit(offset + size)
        val count = size / 2
        return ShortArray(count) {
            if (duplicate.remaining() >= 2) duplicate.short else 0
        }
    }

    private fun toMono16k(raw: ShortArray, channels: Int, sourceRate: Int): ShortArray {
        if (raw.isEmpty()) return raw

        val mono = if (channels > 1) {
            val frames = raw.size / channels
            ShortArray(frames) { frame ->
                var sum = 0
                for (channel in 0 until channels) {
                    sum += raw[frame * channels + channel]
                }
                (sum / channels).toShort()
            }
        } else {
            raw
        }
        if (sourceRate == TARGET_RATE) return mono

        val ratio = sourceRate.toDouble() / TARGET_RATE
        val outputLength = (mono.size / ratio).toInt().coerceAtLeast(1)
        return ShortArray(outputLength) { index ->
            val position = index * ratio
            val a = mono[position.toInt().coerceIn(mono.indices)]
            val b = mono[(position.toInt() + 1).coerceIn(mono.indices)]
            (a + ((b - a) * (position - position.toInt()))).toInt().toShort()
        }
    }
}
