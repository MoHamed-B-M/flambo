package com.pranshulgg.recordmaster.core.record

import android.Manifest
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
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputKind
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

    private fun enumerateInputs(): InputEnumeration {
        val bluetoothAvailable = Build.VERSION.SDK_INT < 31 ||
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

        val result = runCatching {
            context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .orEmpty()
        }
        val inputs = result.getOrElse { emptyArray() }
            .asSequence()
            .mapNotNull { device -> toMicrophoneInput(device, bluetoothAvailable) }
            .distinctBy { it.id }
            .toList()

        return InputEnumeration(
            inputs = inputs,
            enumerationAvailable = result.isSuccess,
            bluetoothEnumerationAvailable = bluetoothAvailable
        )
    }

    private fun toMicrophoneInput(
        device: AudioDeviceInfo,
        bluetoothAvailable: Boolean
    ): MicrophoneInput? {
        if (!device.isSource) return null

        val kind = microphoneKind(device.type) ?: return null
        if (kind == MicrophoneInputKind.BLUETOOTH && !bluetoothAvailable) return null

        val productName = device.productName?.toString()?.trim().orEmpty()
        val address = runCatching { device.address }.getOrDefault("").trim()
        val discriminator = address.ifBlank { productName.ifBlank { device.type.toString() } }
        val prefix = when (kind) {
            MicrophoneInputKind.BUILT_IN -> "builtin"
            MicrophoneInputKind.WIRED -> "wired"
            MicrophoneInputKind.BLUETOOTH -> "bt"
            MicrophoneInputKind.USB -> "usb"
            else -> "input"
        }
        val baseLabel = when (kind) {
            MicrophoneInputKind.BUILT_IN -> "Built-in microphone"
            MicrophoneInputKind.WIRED -> "Wired headset microphone"
            MicrophoneInputKind.BLUETOOTH -> "Bluetooth microphone"
            MicrophoneInputKind.USB -> "USB microphone"
            else -> "Microphone"
        }
        val displayName = if (productName.isBlank() || productName.equals(baseLabel, ignoreCase = true)) {
            baseLabel
        } else {
            "$baseLabel ($productName)"
        }
        val maxChannels = runCatching {
            device.channelCounts?.maxOrNull() ?: 1
        }.getOrDefault(1).coerceAtLeast(1)

        return MicrophoneInput(
            id = "$prefix:$discriminator",
            displayName = displayName,
            kind = kind,
            maxChannels = maxChannels
        )
    }

    /** A2DP and other sinks are deliberately excluded; only source devices are microphones. */
    private fun microphoneKind(type: Int): MicrophoneInputKind? = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> null
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> MicrophoneInputKind.BUILT_IN
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> MicrophoneInputKind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MicrophoneInputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> MicrophoneInputKind.USB
        else -> MicrophoneInputKind.UNKNOWN
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
