package com.pranshulgg.recordmaster.core.record

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Owns the temporary system audio routing needed when a take uses a Bluetooth
 * microphone. API 31+ selects a communication device; older versions open SCO.
 * The route is released as soon as the take ends so the phone's normal routing
 * is restored.
 */
class BluetoothAudioRoute(context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    private var communicationDevice: AudioDeviceInfo? = null
    private var scoActive = false

    /** Returns false when the platform rejects the route; the caller must hard-fail. */
    fun activate(inputDevice: AudioDeviceInfo): Boolean {
        val manager = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val output = communicationOutputFor(manager, inputDevice) ?: return false
            val selected = runCatching { manager.setCommunicationDevice(output) }
                .getOrDefault(false)
            if (selected) communicationDevice = output
            selected
        } else {
            @SuppressLint("MissingPermission") // MODIFY_AUDIO_SETTINGS is declared in the manifest.
            val started = runCatching {
                manager.startBluetoothSco()
                manager.isBluetoothScoOn = true
                true
            }.getOrDefault(false)
            scoActive = started
            started
        }
    }

    fun release() {
        val manager = audioManager ?: return
        if (communicationDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.clearCommunicationDevice() }
            communicationDevice = null
        }
        if (scoActive) {
            @SuppressLint("MissingPermission") // MODIFY_AUDIO_SETTINGS is declared in the manifest.
            runCatching {
                manager.isBluetoothScoOn = false
                manager.stopBluetoothSco()
            }
            scoActive = false
        }
    }

    /**
     * `setCommunicationDevice` only accepts sink devices. Input enumeration
     * returns the source half of a headset, so resolve the matching output by
     * address (or type) among devices the platform marks as communication
     * capable. The platform then selects the paired microphone automatically.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun communicationOutputFor(
        manager: AudioManager,
        inputDevice: AudioDeviceInfo
    ): AudioDeviceInfo? {
        val available = runCatching { manager.getAvailableCommunicationDevices() }
            .getOrDefault(emptyList())
        if (available.isEmpty()) return null
        if (inputDevice.isSink) {
            return available.firstOrNull {
                it.type == inputDevice.type && it.address == inputDevice.address
            }
        }

        val address = inputDevice.address.trim()
        val productName = inputDevice.productName?.toString()?.trim().orEmpty()

        return available.firstOrNull { it.type == inputDevice.type && it.address.equals(address, true) }
            ?: available.firstOrNull {
                productName.isNotBlank() && it.productName?.toString()?.trim() == productName
            }
            ?: available.firstOrNull { it.type == inputDevice.type }
    }
}
