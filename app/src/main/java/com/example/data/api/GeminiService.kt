package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.SermonSegment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiEndpoints {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Read key safely with placeholder check
    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val endpoints: GeminiEndpoints by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiEndpoints::class.java)
    }

    /**
     * Check if API key is provided and is not a placeholder
     */
    fun isApiKeyAvailable(): Boolean {
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && apiKey != "placeholder"
    }

    /**
     * Call the Gemini API to get text response
     */
    suspend fun fetchGeminiContent(prompt: String, systemInstruction: String? = null): String {
        if (!isApiKeyAvailable()) {
            return "Note: Gemini API key is missing. Please set GEMINI_API_KEY in the AI Studio secrets panel.\n\n[Fallback Content]\nThis is a local outline placeholder. Double-check your API key configuration."
        }

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(text = it))) }
        )

        return try {
            val response = endpoints.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response content from Gemini."
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling Gemini API", e)
            "Error: ${e.localizedMessage ?: "Unknown network failure"}"
        }
    }

    /**
     * Post-processing: Correct typos, structure sections, and format Biblical references
     */
    suspend fun correctTranscript(rawTranscript: String): String {
        val systemInstruction = """
            You are an expert editor specializing in sermon transcriptions and theological content.
            Analyze the input raw transcript text. Fix typos, correct bad sentence boundaries, format Bible references (e.g., change 'john chapter 3 verse 16' to 'John 3:16', 'romans 8 28' to 'Romans 8:28'), bold important theological proper nouns, and output the clean, readable transcript text.
            Do not summarize the transcript; preserve all the speaker's original thoughts, but make them grammatically clean and readable. Just return the clean text directly without extra introductory phrases.
        """.trimIndent()

        val prompt = "Please format and correct this transcription:\n\n$rawTranscript"
        return fetchGeminiContent(prompt, systemInstruction)
    }

    /**
     * Create a structured Markdown Sermon Outline
     */
    suspend fun generateSermonOutline(transcript: String): String {
        val systemInstruction = """
            You are a master theologian and homiletics professor.
            Create a highly polished, comprehensive sermon outline in GitHub-Flavored Markdown.
            The outline must include:
            1. Title: Create an engaging, inspiring title based on the text.
            2. Sermon Scripture: Direct key Bible passages referenced.
            3. Introduction: Opening hook and central thesis.
            4. Core Outline (at least 3 main points): Each point should have sub-points (A, B, C) with brief explanations, illustrative concepts, and Biblical cross-references.
            5. Conclusion & Actionable Applications: Brief summary and 2-3 specific behavioral steps for the listeners. Use beautiful format styling, quotes, and bullet points.
        """.trimIndent()

        val prompt = "Create a sermon outline from this transcript:\n\n$transcript"
        return fetchGeminiContent(prompt, systemInstruction)
    }

    /**
     * Compose a high-level briefing/summary
     */
    suspend fun generateSummary(transcript: String): String {
        val systemInstruction = "You are a concise executive summarizer. Write a single, highly refined paragraph (3-4 sentences max) summarizing the main theme, key Biblical texts, and takeaway of this sermon."
        val prompt = "Summarize this lecture/sermon:\n\n$transcript"
        return fetchGeminiContent(prompt, systemInstruction)
    }

    /**
     * Generates a realistic transcript based on a given sermon theme or topic
     */
    suspend fun generateSermonTranscriptByTopic(theme: String): String {
        val systemInstruction = "You are a preacher sharing an inspirational, deep Christian sermon or lecture. Create a realistic, highly engaging 3-4 paragraph sermon draft about the topic. Include Bible quotes and illustrative stories. Print only the preacher's words."
        val prompt = "Generate a sermon transcript about: $theme"
        return fetchGeminiContent(prompt, systemInstruction)
    }
}
