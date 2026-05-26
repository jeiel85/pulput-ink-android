package io.pulpit.ink.data.api

import android.util.Log
import io.pulpit.ink.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified OpenAI client: Whisper for speech-to-text + Chat Completions for
 * post-processing (summary / outline / scripture / proper nouns / title / keywords).
 *
 * One API key (OPENAI_API_KEY) covers both surfaces.
 */
object OpenAIService {
    private const val TAG = "OpenAIService"
    private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
    private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
    private const val CHAT_MODEL = "gpt-4o-mini"
    private const val WHISPER_MODEL = "whisper-1"

    private val apiKey: String
        get() = BuildConfig.OPENAI_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun isApiKeyAvailable(): Boolean {
        return apiKey.isNotBlank() &&
            apiKey != "MY_OPENAI_API_KEY" &&
            apiKey != "placeholder" &&
            apiKey.startsWith("sk-")
    }

    private fun isKorean(): Boolean = Locale.getDefault().language == "ko"

    class OpenAIException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private fun requireKey() {
        if (!isApiKeyAvailable()) {
            throw OpenAIException(
                if (isKorean()) "OpenAI API 키가 설정되지 않았습니다. 프로젝트 루트의 .env 파일에 OPENAI_API_KEY를 추가하세요."
                else "OpenAI API key is not configured. Add OPENAI_API_KEY to the project root .env file."
            )
        }
    }

    /* ---------------------- Whisper STT ---------------------- */

    suspend fun transcribeAudio(audioFile: File, languageHint: String? = null): String =
        withContext(Dispatchers.IO) {
            requireKey()
            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw OpenAIException(
                    if (isKorean()) "오디오 파일이 비어 있거나 존재하지 않습니다."
                    else "Audio file is empty or missing."
                )
            }

            val mediaType = "audio/mp4".toMediaTypeOrNull()
            val requestFile = audioFile.asRequestBody(mediaType)

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, requestFile)
                .addFormDataPart("model", WHISPER_MODEL)
                .addFormDataPart("response_format", "json")

            val effectiveLang = languageHint
                ?: Locale.getDefault().language.takeIf { it.isNotBlank() }
            if (!effectiveLang.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("language", effectiveLang)
            }

            val request = Request.Builder()
                .url(WHISPER_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Whisper error ${response.code}: $body")
                    throw OpenAIException(parseError(body) ?: "Whisper API error ${response.code}")
                }
                val text = JSONObject(body).optString("text", "")
                if (text.isBlank()) {
                    throw OpenAIException(
                        if (isKorean()) "음성에서 인식된 텍스트가 없습니다. 마이크 입력 또는 녹음 길이를 확인하세요."
                        else "No text was recognized from the audio. Check the microphone input and recording length."
                    )
                }
                text
            }
        }

    /* ---------------------- Chat Completions ---------------------- */

    private suspend fun chat(systemInstruction: String, userPrompt: String, temperature: Double = 0.4): String =
        withContext(Dispatchers.IO) {
            requireKey()

            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemInstruction))
                .put(JSONObject().put("role", "user").put("content", userPrompt))

            val payload = JSONObject()
                .put("model", CHAT_MODEL)
                .put("messages", messages)
                .put("temperature", temperature)

            val request = Request.Builder()
                .url(CHAT_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Chat error ${response.code}: $body")
                    throw OpenAIException(parseError(body) ?: "Chat API error ${response.code}")
                }
                val choices = JSONObject(body).optJSONArray("choices") ?: JSONArray()
                if (choices.length() == 0) {
                    throw OpenAIException("Empty chat response")
                }
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim()
            }
        }

    private fun parseError(body: String): String? {
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
    }

    /* ---------------------- Higher-level helpers ---------------------- */

    suspend fun correctTranscript(rawTranscript: String, topicHint: String? = null): String {
        val sys = if (isKorean()) {
            buildString {
                append("당신은 한국어 설교 전사 편집 전문가입니다. ")
                append("입력 텍스트의 맞춤법·띄어쓰기 오류를 교정하고, 성경 인용을 표준 표기('요한복음 3:16')로 정규화하고, 문단을 자연스럽게 나눕니다. ")
                append("절대 요약하거나 새로운 내용을 추가하지 말고, 원문의 의미와 분량을 그대로 유지하세요. ")
                append("결과는 교정된 본문 텍스트만 그대로 반환하세요.")
                if (!topicHint.isNullOrBlank()) append(" 참고 주제: $topicHint")
            }
        } else {
            buildString {
                append("You are an expert sermon-transcript editor. ")
                append("Fix typos and sentence boundaries, normalize Bible references to canonical form (e.g. 'John 3:16'), and split into natural paragraphs. ")
                append("Do not summarize or add content. Preserve the original meaning and length. ")
                append("Return only the corrected transcript text.")
                if (!topicHint.isNullOrBlank()) append(" Topic hint: $topicHint")
            }
        }
        return chat(sys, rawTranscript, temperature = 0.2)
    }

    suspend fun generateTitle(transcript: String): String {
        val sys = if (isKorean()) {
            "당신은 설교 제목 작가입니다. 주어진 설교 본문을 읽고 한국어로 30자 이내의 명확하고 매력적인 제목을 한 줄로 작성하세요. 따옴표나 설명 없이 제목만 출력하세요."
        } else {
            "You are a sermon-title writer. Read the transcript and produce a single concise, compelling English title (max 60 characters). Output only the title — no quotes, no explanation."
        }
        return chat(sys, transcript, temperature = 0.6)
            .lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('"', '\'', '“', '”', '`', '*')
            .orEmpty()
    }

    suspend fun generateSummary(transcript: String): String {
        val sys = if (isKorean()) {
            "당신은 설교 요약 전문가입니다. 핵심 메시지·주요 성경 본문·청중을 향한 권면을 3-4문장의 자연스러운 한국어 단락 하나로 요약하세요. 다른 설명 없이 요약문만 출력하세요."
        } else {
            "You are a concise sermon summarizer. Produce one polished paragraph (3-4 sentences) covering the main theme, key scripture, and takeaway. Output only the summary."
        }
        return chat(sys, transcript, temperature = 0.3)
    }

    suspend fun generateSermonOutline(transcript: String): String {
        val sys = if (isKorean()) {
            """
                당신은 설교학·신학 전문 편집자입니다.
                주어진 설교 본문에서 실제로 다뤄진 내용만을 사용해 강해 연구용 설교 개요서를 GitHub-Flavored Markdown으로 작성하세요.
                반드시 다음을 포함합니다.
                1. 제목 (H1)
                2. 본문 설교 성경 구절 인용 (블록 인용)
                3. 서론 — 도입 화두와 핵심 명제
                4. 본론 (최소 2개 이상의 대지, 각 대지에 핵심 요지·요절·적용)
                5. 결론 및 실천 적용 (구체적 2-3가지)
                전사에 명시적으로 등장하지 않은 성경 구절·인물·일화는 임의로 만들어 넣지 마세요. 부족하면 비워두세요.
                마크다운 본문만 출력하세요.
            """.trimIndent()
        } else {
            """
                You are a homiletics editor. Using only what actually appears in the transcript, produce a study-quality sermon outline in GitHub-Flavored Markdown.
                Required sections:
                1. Title (H1)
                2. Key Scripture (block quote)
                3. Introduction — hook + thesis
                4. Body — at least two points, each with main idea, key verse, and application
                5. Conclusion & Practical Application (2-3 concrete steps)
                Do not invent scripture, names, or anecdotes that aren't in the transcript. If something is missing, leave it out.
                Output only the markdown.
            """.trimIndent()
        }
        return chat(sys, transcript, temperature = 0.4)
    }

    suspend fun extractBibleReferences(transcript: String): String {
        val sys = if (isKorean()) {
            "당신은 성경 인용 추출기입니다. 입력 본문에서 명시적으로 언급된 성경 구절만 표준 표기('요한복음 3:16', '창세기 1:1-3' 등)로 변환해 쉼표로 구분된 한 줄로 출력하세요. 본문에 성경 인용이 전혀 없으면 빈 줄을 출력하세요. 다른 설명은 절대 쓰지 마세요."
        } else {
            "You are a Bible-reference extractor. From the input, extract only references that are explicitly mentioned and output them as a comma-separated single line in canonical form (e.g. 'John 3:16', 'Genesis 1:1-3'). If there are none, output an empty line. Do not write any other text."
        }
        return chat(sys, transcript, temperature = 0.1).lines().firstOrNull()?.trim().orEmpty()
    }

    suspend fun extractProperNouns(transcript: String): String {
        val sys = if (isKorean()) {
            "본문에서 실제로 등장한 신학적 고유명사(인물·지명·교리)만 쉼표로 구분해 한 줄로 출력하세요. 등장한 적이 없는 단어를 만들어 넣지 마세요. 없으면 빈 줄. 설명 금지."
        } else {
            "From the input, extract only proper nouns that actually appear (people, places, doctrines). Output as a single comma-separated line. Do not invent names. If none, output an empty line. No explanation."
        }
        return chat(sys, transcript, temperature = 0.1).lines().firstOrNull()?.trim().orEmpty()
    }

    suspend fun extractKeywords(transcript: String): String {
        val sys = if (isKorean()) {
            "본문의 핵심 키워드 5-8개를 쉼표로 구분해 한 줄로 출력하세요. 본문에 실제로 등장한 단어 위주로 뽑되, 검색에 유용한 짧은 명사형으로 정리하세요. 설명 금지."
        } else {
            "Produce 5-8 search keywords as a single comma-separated line. Prefer short nouns that actually appear in the text. No explanation."
        }
        return chat(sys, transcript, temperature = 0.2).lines().firstOrNull()?.trim().orEmpty()
    }
}
