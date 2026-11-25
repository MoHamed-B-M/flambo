package com.pranshulgg.recordmaster.core.domain

import java.util.Locale

/** Categories kept in the order shown in picker UIs. */
enum class MicrophoneDeviceCategory(val order: Int) {
    BUILT_IN(0),
    BLUETOOTH(1),
    WIRED(2),
    USB(3)
}

/**
 * A platform input reduced to the facts needed for grouping. Keeping this
 * separate from `AudioDeviceInfo` lets the deduplication rules run as JVM tests.
 */
data class MicrophoneDeviceCandidate(
    val category: MicrophoneDeviceCategory,
    val type: Int,
    val address: String,
    val productName: String,
    val maxChannels: Int
)

/**
 * Collapses Android's raw input endpoints into one stable entry per real
 * microphone. Multiple built-in endpoints become one entry; Bluetooth SCO and
 * BLE headset endpoints with the same address become one entry; wired and USB
 * devices are grouped by address so a single device cannot appear twice.
 */
object MicrophoneInputMapper {

    fun map(candidates: List<MicrophoneDeviceCandidate>): List<MicrophoneInput> {
        val groups = LinkedHashMap<String, MutableList<MicrophoneDeviceCandidate>>()
        for (candidate in candidates) {
            val key = groupKey(candidate)
            groups.getOrPut(key) { mutableListOf() } += candidate
        }

        return groups.values
            .mapNotNull(::toMicrophoneInput)
            .sortedWith(compareBy({ categoryOrder(it.kind) }, { it.displayName }))
    }

    /** Stable picker order that follows [MicrophoneDeviceCategory] ordering. */
    fun categoryOrder(kind: MicrophoneInputKind): Int = when (kind) {
        MicrophoneInputKind.BUILT_IN -> MicrophoneDeviceCategory.BUILT_IN.order
        MicrophoneInputKind.BLUETOOTH -> MicrophoneDeviceCategory.BLUETOOTH.order
        MicrophoneInputKind.WIRED -> MicrophoneDeviceCategory.WIRED.order
        MicrophoneInputKind.USB -> MicrophoneDeviceCategory.USB.order
        else -> Int.MAX_VALUE
    }

    private fun groupKey(candidate: MicrophoneDeviceCandidate): String {
        val address = normalized(candidate.address)
        val name = normalized(candidate.productName)
        return when (candidate.category) {
            MicrophoneDeviceCategory.BUILT_IN -> "builtin"
            MicrophoneDeviceCategory.BLUETOOTH -> "bt:${address.ifBlank { name.ifBlank { "device" } }}"
            MicrophoneDeviceCategory.WIRED -> "wired:${address.ifBlank { name.ifBlank { candidate.type.toString() } }}"
            MicrophoneDeviceCategory.USB -> "usb:${address.ifBlank { name.ifBlank { candidate.type.toString() } }}"
        }
    }

    private fun toMicrophoneInput(group: List<MicrophoneDeviceCandidate>): MicrophoneInput? {
        val category = group.firstOrNull()?.category ?: return null
        val address = group.map { it.address.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        val productName = group.map { it.productName.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals(baseLabel(category), ignoreCase = true) }
            .orEmpty()
        val maxChannels = group.maxOf { it.maxChannels.coerceAtLeast(1) }

        return when (category) {
            MicrophoneDeviceCategory.BUILT_IN -> MicrophoneInput(
                id = "builtin:primary",
                displayName = "Built-in microphone",
                kind = MicrophoneInputKind.BUILT_IN,
                maxChannels = maxChannels
            )

            MicrophoneDeviceCategory.BLUETOOTH -> MicrophoneInput(
                id = "bt:${normalized(address).ifBlank { normalized(productName).ifBlank { "device" } }}",
                displayName = labeled(baseLabel(category), productName, address),
                kind = MicrophoneInputKind.BLUETOOTH,
                maxChannels = maxChannels
            )

            MicrophoneDeviceCategory.WIRED -> MicrophoneInput(
                id = "wired:${normalized(address).ifBlank { normalized(productName).ifBlank { "headset" } }}",
                displayName = labeled(baseLabel(category), productName, address),
                kind = MicrophoneInputKind.WIRED,
                maxChannels = maxChannels
            )

            MicrophoneDeviceCategory.USB -> MicrophoneInput(
                id = "usb:${normalized(address).ifBlank { normalized(productName).ifBlank { group.first().type.toString() } }}",
                displayName = labeled(baseLabel(category), productName, address),
                kind = MicrophoneInputKind.USB,
                maxChannels = maxChannels
            )
        }
    }

    private fun baseLabel(category: MicrophoneDeviceCategory): String = when (category) {
        MicrophoneDeviceCategory.BUILT_IN -> "Built-in microphone"
        MicrophoneDeviceCategory.BLUETOOTH -> "Bluetooth microphone"
        MicrophoneDeviceCategory.WIRED -> "Wired headset microphone"
        MicrophoneDeviceCategory.USB -> "USB microphone"
    }

    /**
     * Keep labels distinct when two devices share a product name: a Bluetooth
     * address or USB endpoint address is included in that case.
     */
    private fun labeled(base: String, productName: String, address: String): String {
        if (productName.isBlank()) {
            return if (address.isBlank()) base else "$base ($address)"
        }
        return if (address.isBlank()) "$base ($productName)" else "$base ($productName · $address)"
    }

    private fun normalized(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
