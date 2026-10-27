package com.pranshulgg.recordmaster.core.stt

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WhisperTranscriptionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _state = MutableStateFlow(WhisperState.IDLE)
    val state: StateFlow<WhisperState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // VAD/energy gate parameters
        private const val ENERGY_THRESHOLD = 500.0 // Simple energy threshold
        private const val MIN_SPEECH_DURATION_MS = 1000 // Min 1 second
        private const val MAX_SPEECH_DURATION_MS = 3000 // Max 3 seconds
        private const val FRAME_SIZE_MS = 30 // 30ms frames for VAD
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    fun startListening() {
        if (recordingJob?.isActive == true) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        _state.value = WhisperState.LISTENING
        recordingJob = serviceScope.launch {
            startAudioCapture()
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.value = WhisperState.IDLE
    }

    private suspend fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val audioBufferSize = maxOf(bufferSize * 4, SAMPLE_RATE * 2 * 2) // 2 seconds buffer

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // Bypass AGC and noise suppression
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            audioBufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = WhisperState.IDLE
            return
        }

        audioRecord?.startRecording()
        
        val frameSize = (SAMPLE_RATE * FRAME_SIZE_MS / 1000) * 2 // bytes per frame
        val buffer = ByteArray(frameSize)
        val speechBuffer = ArrayList<Short>()
        var speechStartTime = 0L
        var inSpeech = false
        
        try {
            while (recordingJob?.isActive == true) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val shorts = bytesToShorts(buffer, read)
                    val energy = calculateEnergy(shorts)
                    
                    if (energy > ENERGY_THRESHOLD && !inSpeech) {
                        // Speech started
                        inSpeech = true
                        speechStartTime = System.currentTimeMillis()
                        speechBuffer.clear()
                        speechBuffer.addAll(shorts.toList())
                    } else if (inSpeech) {
                        speechBuffer.addAll(shorts.toList())
                        val duration = System.currentTimeMillis() - speechStartTime
                        
                        if (energy <= ENERGY_THRESHOLD && duration >= MIN_SPEECH_DURATION_MS) {
                            // Speech ended (silence) and meets minimum duration
                            processSpeechSegment(speechBuffer.toShortArray())
                            inSpeech = false
                            speechBuffer.clear()
                        } else if (duration >= MAX_SPEECH_DURATION_MS) {
                            // Max duration reached, force process
                            processSpeechSegment(speechBuffer.toShortArray())
                            inSpeech = false
                            speechBuffer.clear()
                        }
                    }
                    // If not in speech and low energy, ignore (VAD gate)
                }
            }
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    private fun bytesToShorts(bytes: ByteArray, size: Int): ShortArray {
        val shorts = ShortArray(size / 2)
        val buffer = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.LITTLE_ENDIAN)
        for (i in shorts.indices) {
            if (buffer.hasRemaining()) {
                shorts[i] = buffer.short
            }
        }
        return shorts
    }

    private fun calculateEnergy(shorts: ShortArray): Double {
        var sum = 0.0
        for (s in shorts) {
            val sample = s.toDouble()
            sum += sample * sample
        }
        return Math.sqrt(sum / shorts.size)
    }

    private suspend fun processSpeechSegment(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        _state.value = WhisperState.TRANSCRIBING
        // This will be wired to WhisperEngine in Phase 5
        // For now just update state
        _state.value = WhisperState.LISTENING
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        serviceScope.cancel()
    }
}
