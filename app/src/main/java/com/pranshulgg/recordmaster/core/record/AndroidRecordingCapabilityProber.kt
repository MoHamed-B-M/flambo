package com.pranshulgg.recordmaster.core.record

import android.Manifest
import androidx.annotation.RequiresApi
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodecList
import android.os.Build
import androidx.core.content.ContextCompat
import com.pranshulgg.recordmaster.core.domain.MicrophoneDeviceCandidate
import com.pranshulgg.recordmaster.core.domain.MicrophoneDeviceCategory
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class InputEnumeration(
    val inputs: List<MicrophoneInput>,
    val enumerationAvailable: Boolean,
    val bluetoothEnumerationAvailable: Boolean
)

/**
 * Collects device-specific facts once, off the main thread.
 *
 * This class is intentionally not used by local JVM tests; all decisions made
 * from its snapshot live in [RecordingCapabilities] and are covered there.
 */
class RecordingCapabilityProber(private val context: Context) {

    suspend fun probe(): RecordingCapabilitySnapshot = withContext(Dispatchers.IO) {
        val inputs = enumerateInputs()

        RecordingCapabilitySnapshot(
            sdkInt = Build.VERSION.SDK_INT,
            platformAudioEncoderMimes = platformAudioEncoderMimes(),
            pcmCaptureAvailable = pcmCaptureAvailable(),
            bundledAudioEncoders = bundledEncoders(),
            microphoneInputs = inputs.inputs,
            defaultMaxInputChannels = inputs.inputs.maxOfOrNull { it.maxChannels } ?: 1,
            inputEnumerationAvailable = inputs.enumerationAvailable,
            bluetoothEnumerationAvailable = inputs.bluetoothEnumerationAvailable,
            locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            locationServicesEnabled = locationServicesEnabled()
        )
    }

    private fun platformAudioEncoderMimes(): Set<String> = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.orEmpty()
            .asSequence()
            .filter { it.isEncoder }
            .flatMap { it.supportedTypes.orEmpty().asSequence() }
            .map { it.lowercase(Locale.US) }
            .toSet()
    }.getOrDefault(emptySet())

    private fun pcmCaptureAvailable(): Boolean = runCatching {
        AudioRecord.getMinBufferSize(
            44_100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) > 0
    }.getOrDefault(false)

    /** Fresh input list for pickers; a saved id may no longer be present. */
    suspend fun availableInputs(): List<MicrophoneInput> = withContext(Dispatchers.IO) {
        enumerateInputs().inputs
    }

    /** Resolves a persisted [MicrophoneInput.id] back to a live audio source. */
    suspend fun resolveInput(id: String): AudioDeviceInfo? = withContext(Dispatchers.IO) {
        val normalized = id.trim()
        if (normalized.isBlank() ||
            normalized.equals(MicrophoneInput.DEFAULT_ID, ignoreCase = true)
        ) {
            return@withContext null
        }
        val bluetoothAvailable = bluetoothInputEnumerationAvailable()
        if (normalized.startsWith("bt:", ignoreCase = true) && !bluetoothAvailable) {
            return@withContext null
        }

        val devices = inputDevices()
        val candidates = devices.mapNotNull(::toCandidate)
            .filter { bluetoothAvailable || it.category != MicrophoneDeviceCategory.BLUETOOTH }
        val canonicalId = MicrophoneInputMapper.map(candidates)
            .firstOrNull { it.id.equals(normalized, ignoreCase = true) }
            ?.id ?: return@withContext null

        devices.firstOrNull { device ->
            toCandidate(device)?.let { candidate ->
                MicrophoneInputMapper.map(listOf(candidate))
                    .any { it.id.equals(canonicalId, ignoreCase = true) }
            } == true
        }
    }

    private fun bluetoothInputEnumerationAvailable(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT))

    private fun enumerateInputs(): InputEnumeration {
        val bluetoothAvailable = bluetoothInputEnumerationAvailable()
        val result = runCatching { inputDevices() }
        val candidates = result.getOrElse { emptyArray() }
            .asSequence()
            .mapNotNull(::toCandidate)
            .filter { bluetoothAvailable || it.category != MicrophoneDeviceCategory.BLUETOOTH }
            .toList()

        return InputEnumeration(
            inputs = MicrophoneInputMapper.map(candidates),
            enumerationAvailable = result.isSuccess,
            bluetoothEnumerationAvailable = bluetoothAvailable
        )
    }

    private fun inputDevices(): Array<AudioDeviceInfo> = runCatching {
        val devices = context.getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
        devices?.toList()?.toTypedArray() ?: emptyArray()
    }.getOrElse { emptyArray() }

    private fun toCandidate(device: AudioDeviceInfo): MicrophoneDeviceCandidate? {
        if (!device.isSource) return null
        val category = microphoneCategory(device.type) ?: return null

        return MicrophoneDeviceCandidate(
            category = category,
            type = device.type,
            address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                deviceAddress(device)
            } else {
                ""
            },
            productName = runCatching { device.productName?.toString()?.trim() }
                .getOrDefault(null).orEmpty(),
            maxChannels = runCatching { device.channelCounts?.maxOrNull() ?: 1 }
                .getOrDefault(1)
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun deviceAddress(device: AudioDeviceInfo): String =
        runCatching { device.address }.getOrDefault("").trim()

    /**
     * Returns null for outputs and unsupported sources. BLE headsets are only
     * present from API 31; SCO and BLE entries are merged later by address.
     */
    private fun microphoneCategory(type: Int): MicrophoneDeviceCategory? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> MicrophoneDeviceCategory.BUILT_IN
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> MicrophoneDeviceCategory.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MicrophoneDeviceCategory.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> MicrophoneDeviceCategory.USB
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) {
            MicrophoneDeviceCategory.BLUETOOTH
        } else {
            null
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun locationServicesEnabled(): Boolean = runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)

    /** The encoder libraries are compile-time dependencies; check their classes load. */
    private fun bundledEncoders(): Set<String> = buildSet {
        if (isClassAvailable("net.sourceforge.javaflacencoder.FLAC_FileEncoder")) add("flac")
        if (isClassAvailable("com.github.axet.vorbisjni.Vorbis")) add("vorbis")
    }

    private fun isClassAvailable(name: String): Boolean = runCatching {
        Class.forName(name, false, RecordingCapabilityProber::class.java.classLoader)
    }.isSuccess
}
