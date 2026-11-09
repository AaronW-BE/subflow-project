package org.dpdns.alwaysup.subflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NativePngEvidenceTest {
    private lateinit var directory: File
    private lateinit var destination: File

    @Before fun seedOldEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.dpdns.alwaysup.subflow.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "bin94-evidence-").toFile()
        destination = File(directory, "capture.png")
        val old = bitmap(Color.BLUE)
        try {
            destination.outputStream().use { assertTrue(old.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { old.recycle() }
        assertTrue(destination.length() > 0)
    }

    private fun bitmap(color: Int) = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun missingCurrentBitmapCannotReuseOldPng() {
        assertThrows(IllegalStateException::class.java) {
            writeFreshPngEvidence(destination, capture = {
                assertFalse("Old evidence must be deleted before capture", destination.exists())
                null
            })
        }
        assertFalse(destination.exists())
    }

    @Test fun failedCompressionDeletesPartialEvidenceAndRecyclesBitmap() {
        val current = bitmap(Color.RED)
        assertThrows(IllegalStateException::class.java) {
            writeFreshPngEvidence(destination, capture = { current }, encode = { _, stream ->
                stream.write(byteArrayOf(1, 2, 3))
                false
            })
        }
        assertFalse(destination.exists())
        assertTrue(current.isRecycled)
    }

    @Test fun encoderExceptionDeletesPartialEvidenceAndRecyclesBitmap() {
        val current = bitmap(Color.RED)
        assertThrows(IOException::class.java) {
            writeFreshPngEvidence(destination, capture = { current }, encode = { _, stream ->
                stream.write(byteArrayOf(1, 2, 3))
                throw IOException("Injected encoding failure")
            })
        }
        assertFalse(destination.exists())
        assertTrue(current.isRecycled)
    }

    @Test fun successContainsCurrentPixelsRatherThanOldEvidence() {
        val current = bitmap(Color.RED)
        writeFreshPngEvidence(destination, capture = {
            assertFalse(destination.exists())
            current
        })
        assertTrue(current.isRecycled)
        val decoded = BitmapFactory.decodeFile(destination.path)
        assertNotNull(decoded)
        try { assertEquals(Color.RED, decoded.getPixel(0, 0)) } finally { decoded.recycle() }
    }

    @After fun removeEvidenceFixture() {
        if (::directory.isInitialized) directory.deleteRecursively()
    }
}
