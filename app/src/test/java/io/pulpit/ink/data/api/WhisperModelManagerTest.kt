package io.pulpit.ink.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhisperModelManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = WhisperModelManager(context)

    @Test
    fun `resolveModelFile is null when nothing downloaded`() {
        assertNull(manager.resolveModelFile(WhisperModelConfig.TINY))
        assertFalse(manager.isModelDownloaded(WhisperModelConfig.TINY))
    }

    @Test
    fun `resolveModelFile falls back to app storage copy`() {
        val target = File(context.filesDir, WhisperModelConfig.TINY.filename)
        target.writeText("fake-model-bytes")

        val resolved = manager.resolveModelFile(WhisperModelConfig.TINY)
        assertEquals(target.absolutePath, resolved?.absolutePath)
        assertTrue(manager.isModelDownloaded(WhisperModelConfig.TINY))

        target.delete()
    }

    @Test
    fun `base resolves from app storage once downloaded`() {
        assertNull(manager.resolveModelFile(WhisperModelConfig.BASE))

        val target = File(context.filesDir, WhisperModelConfig.BASE.filename)
        target.writeText("fake-base-bytes")
        assertEquals(target.absolutePath, manager.resolveModelFile(WhisperModelConfig.BASE)?.absolutePath)
        assertTrue(manager.isModelDownloaded(WhisperModelConfig.BASE))

        target.delete()
    }

    @Test
    fun `empty file is treated as not downloaded`() {
        val target = File(context.filesDir, WhisperModelConfig.SMALL.filename)
        target.writeText("")

        assertNull(manager.resolveModelFile(WhisperModelConfig.SMALL))
        assertFalse(manager.isModelDownloaded(WhisperModelConfig.SMALL))

        target.delete()
    }
}
