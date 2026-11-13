package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.stt.SpeechModelCatalog
import com.pranshulgg.recordmaster.utils.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM checks for the speech-model catalog and size formatting. The
 * registry itself is covered by device testing because it needs DataStore.
 */
class SpeechModelTest {

    @Test
    fun builtInsHaveStableUniqueIds() {
        val ids = SpeechModelCatalog.BUILT_INS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("tiny"))
        assertTrue(ids.contains("base"))
        assertTrue(ids.contains("small"))
    }

    @Test
    fun everyBuiltInCanBeDownloaded() {
        SpeechModelCatalog.BUILT_INS.forEach { model ->
            assertTrue(model.isBuiltIn)
            assertTrue(model.multilingual)
            assertNotNull(model.sourceUrl)
            assertTrue(model.fileName.endsWith(".bin"))
            assertTrue(model.sizeBytes > 0)
        }
    }

    @Test
    fun defaultIsABuiltIn() {
        assertTrue(SpeechModelCatalog.DEFAULT.isBuiltIn)
        assertNotNull(SpeechModelCatalog.builtIn(SpeechModelCatalog.DEFAULT.id))
    }

    @Test
    fun formatBytesUsesReadableUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        // Decimal separator depends on the device locale, so only the unit and
        // the integer part are asserted.
        assertEquals("1", formatBytes(1024).takeWhile { it.isDigit() })
        assertTrue(formatBytes(1024).endsWith(" KB"))
        assertTrue(formatBytes(42L * 1024 * 1024).endsWith(" MB"))
        assertTrue(formatBytes(1024L * 1024 * 1024).endsWith(" GB"))
    }
}
