package io.pulpit.ink.data.repository

import android.content.Context
import io.pulpit.ink.data.api.SpeechToTextEngineFactory
import io.pulpit.ink.data.text.OfflineTextProcessor
import io.pulpit.ink.data.text.TranscriptionNotificationHelper
import io.pulpit.ink.data.text.TranscriptCleaner
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
     * Transcribe audio with Whisper, then post-process entirely offline
     * (summary / outline / scripture / proper nouns / keywords / title).
     * No API key or internet required.
     */
    suspend fun transcribeJob(
        context: Context,
        jobId: String,
        topicHint: String? = null,
        selectedModelKey: String = "base"
    ): Boolean = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext false
        sermonDao.insertJob(job.copy(status = "Transcribing"))

        val isKo = java.util.Locale.getDefault().language == "ko"
        val title = job.title

        try {
            // Update 1: Init / Start
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, title, 10, 
                if (isKo) "대기 중..." else "Waiting..."
            )

            val audioFile = File(job.audioPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                sermonDao.insertJob(job.copy(
                    status = "Failed",
                    errorMessage = "Audio file missing or empty: ${job.audioPath}"
                ))
                TranscriptionNotificationHelper.dismissNotification(context, jobId)
                return@withContext false
            }

            // Update 2: Audio Decoding Stage
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, title, 20, 
                if (isKo) "오디오 디코딩 중..." else "Decoding audio..."
            )

            // Resolve optimal STT engine dynamically using Factory
            val engine = SpeechToTextEngineFactory.getEngine(context, selectedModelKey)
            
            // Update 3: AI Inference Stage
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, title, 40, 
                if (isKo) "Whisper AI 분석 중..." else "Running Whisper AI inference..."
            )
            val rawTranscript = engine.transcribe(audioFile, selectedModelKey)

            // Update 4: NLP Cleanup Stage
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, title, 75, 
                if (isKo) "맞춤법 교정 및 요약/아웃라인 가공 중..." else "Proofreading & structuring outline..."
            )

            // Local deterministic cleanup (whitespace + Whisper repeat-loop dedup).
            val cleanedTranscript = TranscriptCleaner.clean(rawTranscript)
            
            // Fully Offline High-quality grammar and spacing correction
            val correctedTranscript = OfflineTextProcessor.correctTranscript(cleanedTranscript)
            writeSegments(jobId, job.durationSec, correctedTranscript)

            // Fully Offline post-processing
            val finalTitle = computeOfflineTitle(job.title, correctedTranscript)
            val summary = OfflineTextProcessor.generateSummary(finalTitle, correctedTranscript)
            val refs = OfflineTextProcessor.extractBibleReferences(correctedTranscript)
            val outline = OfflineTextProcessor.generateSermonOutline(finalTitle, correctedTranscript, refs)
            val nouns = OfflineTextProcessor.extractProperNouns(correctedTranscript)
            val keywords = OfflineTextProcessor.extractKeywords(correctedTranscript)

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

            // Update 5: Completed 100%
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, finalTitle, 100, 
                if (isKo) "가공 완료!" else "Transcription successful!"
            )
            return@withContext true
        } catch (e: Exception) {
            sermonDao.insertJob(
                job.copy(
                    status = "Failed",
                    errorMessage = e.localizedMessage ?: "Transcription failed"
                )
            )
            // Error Notification State
            TranscriptionNotificationHelper.showProgressNotification(
                context, jobId, title, 100, 
                if (isKo) "전사 분석 실패" else "Transcription failed"
            )
            return@withContext false
        }
    }

    /**
     * Re-run correction on the existing transcript segments entirely offline.
     */
    suspend fun runAutoCorrect(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext false
        val segments = sermonDao.getSegmentsByJobId(jobId)
        if (segments.isEmpty()) return@withContext false

        sermonDao.insertJob(job.copy(status = "Transcribing"))
        try {
            val rawFulltext = segments.joinToString("\n\n") { it.text }

            val cleaned = TranscriptCleaner.clean(rawFulltext)
            val corrected = OfflineTextProcessor.correctTranscript(cleaned)
            writeSegments(jobId, job.durationSec, corrected)
            
            val summary = OfflineTextProcessor.generateSummary(job.title, corrected)
            val refs = OfflineTextProcessor.extractBibleReferences(corrected)
            val outline = OfflineTextProcessor.generateSermonOutline(job.title, corrected, refs)
            val nouns = OfflineTextProcessor.extractProperNouns(corrected)
            val keywords = OfflineTextProcessor.extractKeywords(corrected)

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
     * Regenerate outline + summary from existing segments entirely offline.
     */
    suspend fun regenerateOutline(jobId: String): Boolean = withContext(Dispatchers.IO) {
        val job = sermonDao.getJobById(jobId) ?: return@withContext false
        val segments = sermonDao.getSegmentsByJobId(jobId)
        if (segments.isEmpty()) return@withContext false

        try {
            val text = segments.joinToString("\n\n") { it.text }
            val refs = OfflineTextProcessor.extractBibleReferences(text)
            val newOutline = OfflineTextProcessor.generateSermonOutline(job.title, text, refs)
            val newSummary = OfflineTextProcessor.generateSummary(job.title, text)
            sermonDao.insertJob(
                job.copy(
                    outline = newOutline,
                    summary = newSummary,
                    bibleRefs = refs
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ---------------------- helpers ---------------------- */

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

    private fun computeOfflineTitle(currentTitle: String, transcript: String): String {
        val placeholderTitles = setOf("Untitled Sermon", "무제 설교", "")
        val needsAutoTitle = currentTitle.trim() in placeholderTitles ||
            currentTitle.startsWith("Sermon_Rec_")
        return if (needsAutoTitle) fallbackTitleFromTranscript(transcript) else currentTitle
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
