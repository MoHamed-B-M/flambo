package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.domain.MicrophoneDeviceCandidate
import com.pranshulgg.recordmaster.core.domain.MicrophoneDeviceCategory
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputKind
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for collapsing raw Android input endpoints. */
class MicrophoneInputMapperTest {

    private fun candidate(
        category: MicrophoneDeviceCategory,
        type: Int = category.ordinal + 1,
        address: String = "",
        productName: String = "",
        maxChannels: Int = 1
    ) = MicrophoneDeviceCandidate(category, type, address, productName, maxChannels)

    @Test
    fun builtinEndpointsCollapseIntoOneEntry() {
        val mapped = MicrophoneInputMapper.map(
            listOf(
                candidate(MicrophoneDeviceCategory.BUILT_IN, type = 1, address = "builtin/0", maxChannels = 1),
                candidate(MicrophoneDeviceCategory.BUILT_IN, type = 1, address = "builtin/1", maxChannels = 2),
                candidate(MicrophoneDeviceCategory.BUILT_IN, type = 1, address = "builtin/2", maxChannels = 1)
            )
        )

        assertEquals(1, mapped.size)
        assertEquals("builtin:primary", mapped.single().id)
        assertEquals("Built-in microphone", mapped.single().displayName)
        assertEquals(2, mapped.single().maxChannels)
    }

    @Test
    fun bluetoothScoAndBleWithSameAddressBecomeOneEntry() {
        val mapped = MicrophoneInputMapper.map(
            listOf(
                candidate(MicrophoneDeviceCategory.BLUETOOTH, type = 7, address = "AA:BB:CC:DD:EE:FF", productName = "Pixel Buds"),
                candidate(MicrophoneDeviceCategory.BLUETOOTH, type = 30, address = "aa:bb:cc:dd:ee:ff", productName = "Pixel Buds"),
                candidate(MicrophoneDeviceCategory.BLUETOOTH, type = 7, address = "AA:BB:CC:DD:EE:FF", productName = "Pixel Buds")
            )
        )

        assertEquals(1, mapped.size)
        assertEquals(MicrophoneInputKind.BLUETOOTH, mapped.single().kind)
        assertEquals("bt:aa:bb:cc:dd:ee:ff", mapped.single().id)
        assertTrue(mapped.single().displayName.contains("Pixel Buds"))
    }

    @Test
    fun differentBluetoothAddressesRemainDistinctEvenWithSameProductName() {
        val mapped = MicrophoneInputMapper.map(
            listOf(
                candidate(MicrophoneDeviceCategory.BLUETOOTH, type = 7, address = "AA:01", productName = "Buds"),
                candidate(MicrophoneDeviceCategory.BLUETOOTH, type = 7, address = "BB:02", productName = "Buds")
            )
        )

        assertEquals(2, mapped.size)
        assertEquals(2, mapped.map { it.id }.toSet().size)
        assertTrue(mapped.all { it.displayName.contains("Buds") })
    }

    @Test
    fun wiredAndUsbDevicesAreGroupedByAddress() {
        val mapped = MicrophoneInputMapper.map(
            listOf(
                candidate(MicrophoneDeviceCategory.WIRED, type = 4, address = "wired/0", productName = "Headset"),
                candidate(MicrophoneDeviceCategory.WIRED, type = 4, address = "wired/0", productName = "Headset"),
                candidate(MicrophoneDeviceCategory.USB, type = 18, address = "usb/1", productName = "Mic"),
                candidate(MicrophoneDeviceCategory.USB, type = 22, address = "usb/1", productName = "Mic")
            )
        )

        assertEquals(2, mapped.size)
        assertEquals(MicrophoneInputKind.WIRED, mapped[0].kind)
        assertEquals(MicrophoneInputKind.USB, mapped[1].kind)
        assertEquals(1, mapped.count { it.kind == MicrophoneInputKind.WIRED })
        assertEquals(1, mapped.count { it.kind == MicrophoneInputKind.USB })
    }

    @Test
    fun categoriesAreOrderedBuiltinBluetoothWiredUsb() {
        val mapped = MicrophoneInputMapper.map(
            listOf(
                candidate(MicrophoneDeviceCategory.USB, address = "usb/1"),
                candidate(MicrophoneDeviceCategory.WIRED, address = "wired/1"),
                candidate(MicrophoneDeviceCategory.BLUETOOTH, address = "bt/1"),
                candidate(MicrophoneDeviceCategory.BUILT_IN, address = "builtin/1")
            )
        )

        assertEquals(
            listOf(
                MicrophoneInputKind.BUILT_IN,
                MicrophoneInputKind.BLUETOOTH,
                MicrophoneInputKind.WIRED,
                MicrophoneInputKind.USB
            ),
            mapped.map { it.kind }
        )
    }
}
