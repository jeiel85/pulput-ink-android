package io.pulpit.ink.data.repository

import android.content.Context
import io.pulpit.ink.data.api.OpenAIService
import io.pulpit.ink.data.db.SermonDao
import io.pulpit.ink.data.model.SermonJob
import io.pulpit.ink.data.model.SermonSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

class SermonRepository(private val sermonDao: SermonDao) {

    val allJobs: Flow<List<SermonJob>> = sermonDao.getAllJobsFlow()

    fun getJobByIdFlow(jobId: String): Flow<SermonJob?> = sermonDao.getJobByIdFlow(jobId)

    fun getSegmentsFlow(jobId: String): Flow<List<SermonSegment>> = sermonDao.getSegmentsByJobIdFlow(jobId)

    suspend fun deleteJob(jobId: String) {
        sermonDao.deleteJobById(jobId)
    }

    suspend fun updateSegmentText(segmentId: Int, text: String) {
        sermonDao.updateSegmentText(segmentId, text)
    }

    suspend fun createNewJob(title: String, audioPath: String, durationSec: Double): String {
        val jobId = UUID.randomUUID().toString()
        val job = SermonJob(
            id = jobId,
            title = title,
            audioPath = audioPath,
            durationSec = durationSec,
            status = "Pending"
        )
        sermonDao.insertJob(job)
        return jobId
    }

    /**
     * Transcribe audio with Whisper, then post-process with GPT (summary / outline /
     * scripture / proper nouns / keywords / title). Fails fast and surfaces a real error
     * via job.status="Failed" when the API key is missing or any step throws.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribeJob(
        context: Context,
        jobId: String,
        topicHint: String? = null,
        selectedModelKey: String = "base"
    ) = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext
        sermonDao.insertJob(job.copy(status = "Transcribing"))

        try {
            val audioFile = File(job.audioPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                sermonDao.insertJob(job.copy(
                    status = "Failed",
                    errorMessage = "Audio file missing or empty: ${job.audioPath}"
                ))
                return@withContext
            }

            val rawTranscript = OpenAIService.transcribeAudio(audioFile)
            writeSegments(jobId, job.durationSec, rawTranscript)

            val finalTitle = computeFinalTitle(job.title, rawTranscript)

            // Post-processing in parallel
            val (summary, outline, refs, nouns, keywords) = runPostProcessing(rawTranscript, topicHint)

            sermonDao.insertJob(
                job.copy(
                    title = finalTitle,
                    status = "Done",
                    summary = summary,
                    outline = outline,
                    bibleRefs = refs,
                    properNouns = nouns,
                    keywords = keywords
                )
            )
        } catch (e: Exception) {
            sermonDao.insertJob(
                job.copy(
                    status = "Failed",
                    errorMessage = e.localizedMessage ?: "Transcription failed"
                )
            )
        }
    }

    /**
     * Re-run GPT correction on the existing transcript segments and refresh derived
     * metadata. No-op (returns false) if no segments exist or the API key is missing.
     */
    suspend fun runAutoCorrect(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext false
        val segments = sermonDao.getSegmentsByJobId(jobId)
        if (segments.isEmpty() || !OpenAIService.isApiKeyAvailable()) return@withContext false

        sermonDao.insertJob(job.copy(status = "Transcribing"))
        try {
            val rawFulltext = segments.joinToString("\n\n") { it.text }
            val corrected = OpenAIService.correctTranscript(rawFulltext)
            writeSegments(jobId, job.durationSec, corrected)

            val (summary, outline, refs, nouns, keywords) = runPostProcessing(corrected, topicHint = null)
            sermonDao.insertJob(
                job.copy(
                    status = "Done",
                    summary = summary,
                    outline = outline,
                    bibleRefs = refs,
                    properNouns = nouns,
                    keywords = keywords
                )
            )
            true
        } catch (e: Exception) {
            sermonDao.insertJob(job.copy(status = "Done", errorMessage = e.localizedMessage))
            false
        }
    }

    /**
     * Regenerate just the outline + summary from existing segments.
     */
    suspend fun regenerateOutline(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext false
        val segments = sermonDao.getSegmentsByJobId(jobId)
        if (segments.isEmpty() || !OpenAIService.isApiKeyAvailable()) return@withContext false

        try {
            val text = segments.joinToString("\n\n") { it.text }
            val newOutline = OpenAIService.generateSermonOutline(text)
            val newSummary = OpenAIService.generateSummary(text)
            sermonDao.insertJob(job.copy(outline = newOutline, summary = newSummary))
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ---------------------- helpers ---------------------- */

    private data class Derived(
        val summary: String,
        val outline: String,
        val refs: String,
        val nouns: String,
        val keywords: String
    )

    private suspend fun runPostProcessing(transcript: String, topicHint: String?): Derived = coroutineScope {
        val summaryD = async { OpenAIService.generateSummary(transcript) }
        val outlineD = async { OpenAIService.generateSermonOutline(transcript) }
        val refsD = async { OpenAIService.extractBibleReferences(transcript) }
        val nounsD = async { OpenAIService.extractProperNouns(transcript) }
        val keywordsD = async { OpenAIService.extractKeywords(transcript) }
        Derived(
            summary = summaryD.await(),
            outline = outlineD.await(),
            refs = refsD.await(),
            nouns = nounsD.await(),
            keywords = keywordsD.await()
        )
    }

    private suspend fun writeSegments(jobId: String, totalDurationSec: Double, transcript: String) {
        val paragraphs = transcript
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(transcript.trim()).filter { it.isNotEmpty() } }

        if (paragraphs.isEmpty()) return

        val perParagraph = if (totalDurationSec > 0) totalDurationSec / paragraphs.size else 0.0
        val isKo = Locale.getDefault().language == "ko"
        val speakerLabel = if (isKo) "설교자" else "Preacher"

        val segments = paragraphs.mapIndexed { index, paragraph ->
            SermonSegment(
                jobId = jobId,
                startSec = index * perParagraph,
                endSec = (index + 1) * perParagraph,
                text = paragraph,
                speaker = speakerLabel
            )
        }
        sermonDao.deleteSegmentsByJobId(jobId)
        sermonDao.insertSegments(segments)
    }

    private suspend fun computeFinalTitle(currentTitle: String, transcript: String): String {
        val placeholderTitles = setOf("Untitled Sermon", "무제 설교", "")
        val needsAutoTitle = currentTitle.trim() in placeholderTitles ||
            currentTitle.startsWith("Sermon_Rec_")
        if (!needsAutoTitle) return currentTitle

        return try {
            OpenAIService.generateTitle(transcript).ifBlank {
                fallbackTitleFromTranscript(transcript)
            }
        } catch (_: Exception) {
            fallbackTitleFromTranscript(transcript)
        }
    }

    private fun fallbackTitleFromTranscript(transcript: String): String {
        val firstSentence = transcript
            .split(Regex("[.。\\n]"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: transcript.take(40)
        return firstSentence.take(40)
    }
}
