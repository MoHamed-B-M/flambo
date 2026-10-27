package com.pranshulgg.recordmaster.core.stt

import android.Manifest
import android.app.Service
import android.content.Context
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

class WhisperTranscriptionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordingJob: Job? = null
    private var inferenceJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val isRunning = AtomicBoolean(false)
    private val pcmQueue = ArrayBlockingQueue<ShortArray>(10) // Ring buffer for backpressure

    private val _state = MutableStateFlow(WhisperState.IDLE)
    val state: StateFlow<WhisperState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()

    private val _transcriptionResult = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val transcriptionResult: SharedFlow<String> = _transcriptionResult.asSharedFlow()

    private lateinit var transcriptionManager: TranscriptionManager
    private lateinit var whisperEngine: WhisperEngine

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

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as? com.pranshulgg.recordmaster.RecordMasterApp
        transcriptionManager = app?.transcription ?: TranscriptionManager(this, com.pranshulgg.recordmaster.core.data.PreferencesManager(this))
        whisperEngine = WhisperEngine()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    fun startListening() {
        if (isRunning.get()) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRunning.set(true)
        _state.value = WhisperState.MODEL_LOADING
        
        serviceScope.launch {
            // Ensure model is loaded
            val loaded = transcriptionManager.ensureModelLoaded()
            if (!loaded) {
                _state.value = WhisperState.IDLE
                isRunning.set(false)
                return@launch
            }
            
            _state.value = WhisperState.LISTENING
            startInferenceLoop()
            recordingJob = launch {
                startAudioCapture()
            }
        }
    }

    fun stopListening() {
        isRunning.set(false)
        recordingJob?.cancel()
        recordingJob = null
        inferenceJob?.cancel()
        inferenceJob = null
        pcmQueue.clear()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _state.value = WhisperState.IDLE
    }

    private fun startInferenceLoop() {
        inferenceJob?.cancel()
        inferenceJob = serviceScope.launch(Dispatchers.Default) {
            val ctxPtr = getContextPtr()
            if (ctxPtr == 0L) {
                _state.value = WhisperState.IDLE
                return@launch
            }
            
            while (isRunning.get()) {
                try {
                    val pcmChunk = pcmQueue.take() // Blocking take for backpressure
                    if (!isRunning.get()) break
                    
                    _state.value = WhisperState.TRANSCRIBING
                    withContext(Dispatchers.IO) {
                        val text = whisperEngine.nativeTranscribe(ctxPtr, pcmChunk)
                        if (text.isNotBlank()) {
                            _finalText.value = text
                            _transcriptionResult.emit(text)
                        }
                    }
                    _state.value = if (isRunning.get()) WhisperState.LISTENING else WhisperState.IDLE
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        _state.value = WhisperState.LISTENING
                    }
                }
            }
        }
    }

    private fun getContextPtr(): Long {
        // Access via reflection or store reference
        return try {
            val field = transcriptionManager::class.java.getDeclaredField("ctxPtr")
            field.isAccessible = true
            field.getLong(transcriptionManager)
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val audioBufferSize = maxOf(bufferSize * 8, SAMPLE_RATE * 2 * 3) // 3 seconds buffer

        audioRecord = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Bypass AGC and noise suppression
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                audioBufferSize
            )
        } else {
            null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = WhisperState.IDLE
            isRunning.set(false)
            return
        }

        audioRecord?.startRecording()
        
        val frameSize = (SAMPLE_RATE * FRAME_SIZE_MS / 1000) * 2 // bytes per frame
        val buffer = ByteArray(frameSize)
        val speechBuffer = ArrayList<Short>(SAMPLE_RATE * 3) // Max 3 seconds
        var speechStartTime = 0L
        var inSpeech = false
        
        try {
            while (isRunning.get() && recordingJob?.isActive == true) {
                val read = try {
                    audioRecord?.read(buffer, 0, buffer.size) ?: 0
                } catch (e: SecurityException) {
                    0
                }
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
                            // Speech ended (silence) and meets minimum duration - chunk into 1-3s
                            val chunk = speechBuffer.toShortArray()
                            if (chunk.isNotEmpty()) {
                                // Use ring buffer with backpressure - drop oldest if full
                                if (!pcmQueue.offer(chunk)) {
                                    pcmQueue.poll()
                                    pcmQueue.offer(chunk)
                                }
                            }
                            inSpeech = false
                            speechBuffer.clear()
                        } else if (duration >= MAX_SPEECH_DURATION_MS) {
                            // Max duration reached (3s) - force process
                            val chunk = speechBuffer.toShortArray()
                            if (chunk.isNotEmpty()) {
                                if (!pcmQueue.offer(chunk)) {
                                    pcmQueue.poll()
                                    pcmQueue.offer(chunk)
                                }
                            }
                            inSpeech = false
                            speechBuffer.clear()
                        }
                    }
                    // If not in speech and low energy, ignore (VAD gate)
                }
            }
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
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
        return sqrt(sum / shorts.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        serviceScope.cancel()
    }
}
