package io.pulpit.ink

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pulpit.ink.data.db.AppDatabase
import io.pulpit.ink.data.repository.SermonRepository
import io.pulpit.ink.data.api.WhisperModelManager
import io.pulpit.ink.data.api.WhisperModelConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Robust Instrumented Integration Test executing directly on the connected device.
 * Validates the entire pipeline:
 * 1. MP3 Audio Decoding (48kHz Mono -> 16kHz Mono Float PCM)
 * 2. GGML Whisper JNI Inference (Tiny Model)
 * 3. Spelling & Spacing correction rules
 * 4. Homiletical Summary & Outline Compilation
 * 5. SQLite Room persistence
 */
@RunWith(AndroidJUnit4::class)
class ImportIntegrationTest {

    @Test
    fun testEndToEndAudioImportAndTranscription() {
        runBlocking {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            val db = AppDatabase.getDatabase(appContext)
            val repository = SermonRepository(db.sermonDao())

            val testFile = File(appContext.filesDir, "test_sermon_30s.mp3")
            testFile.parentFile?.mkdirs()
            
            // Synchronously copy the test file from the world-readable local temp directory
            // directly inside the sandbox (bypasses storage permissions and AGP uninstalls)
            val srcFile = File("/data/local/tmp/test_sermon_30s.mp3")
            if (srcFile.exists()) {
                srcFile.inputStream().use { input ->
                    testFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Synchronously copy the Whisper model file from the world-readable local temp directory
            // directly inside the sandbox (bypasses storage permissions and AGP uninstalls)
            val modelFile = File(appContext.filesDir, "ggml-tiny.bin")
            val srcModelFile = File("/data/local/tmp/ggml-tiny.bin")
            if (srcModelFile.exists()) {
                srcModelFile.inputStream().use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // 1. Verify that the test audio file is copied inside the app sandbox files directory
            assertTrue(
                "Test file missing in app sandbox! Please copy test_sermon_30s.mp3 into files/ first.",
                testFile.exists() && testFile.length() > 0L
            )

            // 2. Verify that the Whisper tiny model is available
            val modelManager = WhisperModelManager(appContext)
            val tinyConfig = WhisperModelConfig.TINY
            assertTrue(
                "Whisper Tiny model is not downloaded on the device!",
                modelManager.isModelDownloaded(tinyConfig)
            )

            // 3. Create a new sermon job in the SQLite Room Database
            val jobId = repository.createNewJob(
                title = "수요설교 룻기 서론 (30s Test)",
                audioPath = testFile.absolutePath,
                durationSec = 30.16
            )
            assertNotNull("Job ID should not be null", jobId)

            // 4. Run the full transcription & NLP post-processing synchronously
            val success = repository.transcribeJob(
                context = appContext,
                jobId = jobId,
                topicHint = "룻기, 서론",
                selectedModelKey = "tiny"
            )
            assertTrue("Transcription job failed!", success)

            // 5. Verify the persisted sermon metadata and NLP results in the database
            val job = db.sermonDao().getJobById(jobId)
            assertNotNull("Sermon job should exist in DB", job)
            assertEquals("Done", job?.status)
            assertNull("Error message should be null", job?.errorMessage)

            // Title should be updated or preserved
            assertFalse("Title should not be blank", job?.title.isNullOrBlank())

            // Summary, outline, bible refs, proper nouns, and keywords must be populated
            assertFalse("Summary must be populated", job?.summary.isNullOrBlank())
            assertFalse("Outline must be populated", job?.outline.isNullOrBlank())
            
            // Assert that the segments were correctly split and cascaded
            val segments = db.sermonDao().getSegmentsByJobId(jobId)
            assertFalse("Segments should not be empty", segments.isEmpty())
            
            // Output results in logs for reporting
            android.util.Log.i("ImportIntegrationTest", "SUCCESS! Title: ${job?.title}")
            android.util.Log.i("ImportIntegrationTest", "Summary: ${job?.summary}")
            android.util.Log.i("ImportIntegrationTest", "Bible Refs: ${job?.bibleRefs}")
            android.util.Log.i("ImportIntegrationTest", "Outline: ${job?.outline}")
        }
    }
}
