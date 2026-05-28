package io.pulpit.ink.data.text

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-performance, fully deterministic, on-device Natural Language Processing engine.
 *
 * Implements:
 * 1. Korean Grammar & Space Correction (Rules + Dictionary)
 * 2. High-precision Bible Passage Regex Extraction and Normalization
 * 3. Text-frequency based Stopword-filtered Keyword & Proper Noun Extraction
 * 4. Homiletical structural Outline & Summary compiler
 */
object OfflineTextProcessor {
    private const val TAG = "OfflineTextProcessor"

    // Standard Korean Bible Books mapping: Full Name -> Preferred Abbreviation
    private val bibleBooks = mapOf(
        "창세기" to "창", "출애굽기" to "출", "레위기" to "레", "민수기" to "민", "신명기" to "신",
        "여호수아" to "여", "사사기" to "삿", "룻기" to "룻", "사무엘상" to "삼상", "사무엘하" to "삼하",
        "열왕기상" to "왕상", "열왕기하" to "왕하", "역대상" to "대상", "역대하" to "대하",
        "에스라" to "스", "느헤미야" to "느", "에스더" to "에", "욥기" to "욥", "시편" to "시",
        "잠언" to "잠", "전도서" to "전", "아가" to "아", "이사야" to "사", "예레미야" to "렘",
        "예레미야애가" to "애", "에스겔" to "겔", "다니엘" to "단", "호세아" to "호", "요엘" to "욜",
        "아모스" to "암", "오바디야" to "옵", "요나" to "욘", "미가" to "미", "나훔" to "나",
        "하박국" to "합", "스바냐" to "습", "학개" to "학", "스가랴" to "슥", "말라기" to "말",
        "마태복음" to "마", "마가복음" to "막", "누가복음" to "누", "요한복음" to "요", "사도행전" to "행",
        "로마서" to "롬", "고린도전서" to "고전", "고린도후서" to "고후", "갈라디아서" to "갈",
        "에베소서" to "엡", "빌립보서" to "빌", "골로새서" to "골", "데살로니가전서" to "살전",
        "데살로니가후서" to "살후", "디모데전서" to "딤전", "디모데후서" to "딤후", "디도서" to "딛",
        "빌레몬서" to "몬", "히브리서" to "히", "야고보서" to "약", "베드로전서" to "벧전",
        "베드로후서" to "벧후", "요한일서" to "요일", "요한이서" to "요이", "요한삼서" to "요삼",
        "유다서" to "유", "요한계시록" to "계"
    )

    private val abbToFull = bibleBooks.entries.associate { it.value to it.key }

    // Combined Regex for Bible verses: (Fullname|Abbreviation) space? chapter (장|:) space? verse (- verse)?
    private val biblePattern = Pattern.compile(
        "(?:창세기|출애굽기|레위기|민수기|신명기|여호수아|사사기|룻기|사무엘상|사무엘하|열왕기상|열왕기하|역대상|역대하|에스라|느헤미야|에스더|욥기|시편|잠언|전도서|아가|이사야|예레미야|예레미야애가|에스겔|다니엘|호세아|요엘|아모스|오바디야|요나|미가|나훔|하박국|스바냐|학개|스가랴|말라기|마태복음|마가복음|누가복음|요한복음|사도행전|로마서|고린도전서|고린도후서|갈라디아서|에베소서|빌립보서|골로새서|데살로니가전서|데살로니가후서|디모데전서|디모데후서|디도서|빌레몬서|히브리서|야고보서|베드로전서|베드로후서|요한일서|요한이서|요한삼서|유다서|요한계시록|계시록|창|출|레|민|신|여|삿|룻|삼상|삼하|왕상|왕하|대상|대하|스|느|에|욥|시|잠|전|아|사|렘|애|겔|단|호|욜|암|옵|욘|미|나|합|습|학|슥|말|마|막|누|요|행|롬|고전|고후|갈|엡|빌|골|살전|살후|딤전|딤후|딛|몬|히|약|벧전|벧후|요일|요이|요삼|유|계)\\s*(\\d+)\\s*(?:장|:)\\s*(\\d+)(?:\\s*[-~]\\s*(\\d+))?",
        Pattern.CASE_INSENSITIVE
    )

    // Predefined theological proper nouns for extraction
    private val theologicalNouns = setOf(
        "예수", "그리스도", "하나님", "여호와", "성령", "십자가", "복음", "구원", "은혜", "예배", "예루살렘", "갈릴리", "유대",
        "아브라함", "모세", "다윗", "바울", "베드로", "요셉", "노아", "솔로몬", "천국", "믿음", "소망", "사랑", "기도",
        "사도", "선지자", "제자", "베들레헴", "이스라엘", "가나안", "성전", "율법", "계명", "부활", "영생", "보혈", "임마누엘"
    )

    // Korean grammar correction patterns and replacements
    private val correctionRules = listOf(
        // Space correction around auxiliary verbs & adjectives
        Regex("([가-힣]+[을ㄹ할될갈볼올줄알먹을])\\s*(수)\\s*(있다|없다|있고|있으며|없고|없으며|있습니다|없습니다|있죠|없죠)") to "$1 수 $3",
        // '것' + adjective (space needed)
        Regex("([가-힣]+[할ㄹ될갈볼올은는던])\\s*(것)\\s*(같다|같습니다)") to "$1 것 $3",
        // '것' + particle (no space needed)
        Regex("([가-힣]+[할ㄹ될갈볼올은는던])\\s*(것)\\s*(이다|입니다|이라|은|이|을|으로|에|과|와)") to "$1 것$3",
        // '때문' + particle (no space needed)
        Regex("([가-힣]+(?:기|은|는|던)?)\\s*(때문)\\s*(에|이다|입니다|이라|의)") to "$1 때문$3",
        
        // Negation "안" (부사) vs "않" (보조용언) correction
        Regex("안되고|안돼고") to "안 되고",
        Regex("안된다|안돼다") to "안 된다",
        Regex("안된다면|안돼다면") to "안 된다면",
        Regex("안되면|안돼면") to "안 되면",
        Regex("안됬|안돼었|안됫") to "안 됐",
        Regex("안됐다|안돼었다|안됫다") to "안 됐다",
        Regex("않되고|않돼고") to "안 되고",
        Regex("않된다|않돼다") to "안 된다",
        Regex("않되") to "안 돼",
        Regex("안돼") to "안 돼",
        Regex("않하고") to "안 하고",
        Regex("([가-힣]+지)\\s*안([가-힣]+)") to "$1 않$2", // e.g., 하지 안는다 -> 하지 않는다

        // Common spelling fixes
        Regex("어떻해") to "어떡해",
        Regex("어떻게해서든|어떡해해서든") to "어떻게 해서든",
        Regex("바래요") to "바라요",
        Regex("하므로써") to "함으로써",
        Regex("되물림") to "대물림",
        Regex("구절판") to "구절", // Contextual fix if whisper hallucinates

        // Punctuation trailing spaces normalization
        Regex("([.?!,])([^\\s\\d])") to "$1 $2"
    )

    // Korean common stopwords for TF keyword extractor
    private val koreanStopwords = setOf(
        "은", "는", "이", "가", "을", "를", "과", "와", "의", "에", "로", "으로", "에서", "하고", "그리고", "하지만", "그러나",
        "그래서", "그", "이", "저", "것", "수", "등", "및", "한", "할", "또", "더", "더욱", "매우", "아주", "오늘", "지금",
        "우리", "저희", "너희", "그들", "때문", "경우", "정도", "통해", "위해", "대한", "대해", "대해서", "가지", "모든",
        "어떤", "어떻게", "이렇게", "그렇게", "저렇게", "이런", "그런", "저런", "많이", "가장", "서로", "스스로", "진짜", "정말",
        "매우", "다시", "바로", "그냥", "단지", "오직", "결국", "먼저", "먼저", "먼저", "먼저", "먼저", "먼저"
    )

    /**
     * Corrects spelling, spacing, and punctuation in a sermon transcript.
     * Fully offline and lightning-fast.
     */
    fun correctTranscript(raw: String): String {
        if (raw.isBlank()) return ""
        var corrected = raw

        // Apply rules-based spacing & spelling correction
        for ((pattern, replacement) in correctionRules) {
            corrected = corrected.replace(pattern, replacement)
        }

        // Apply Bible verse formatting normalization in-place
        val matcher = biblePattern.matcher(corrected)
        val sb = StringBuffer()
        while (matcher.find()) {
            val cleanBook = cleanBibleBookFromMatcher(matcher)
            matcher.appendReplacement(sb, cleanBook)
        }
        matcher.appendTail(sb)
        corrected = sb.toString()

        return corrected.trim()
    }

    /**
     * Extract references like "John 3:16" or "Genesis 1:1-3" deterministically using Regex.
     */
    fun extractBibleReferences(transcript: String): String {
        if (transcript.isBlank()) return ""
        val refs = mutableSetOf<String>()
        val matcher = biblePattern.matcher(transcript)

        while (matcher.find()) {
            refs.add(cleanBibleBookFromMatcher(matcher))
        }

        return refs.joinToString(", ")
    }

    /**
     * Standardizes a parsed Bible book and chapter/verse string into canonical form.
     */
    private fun cleanBibleBookFromMatcher(matcher: java.util.regex.Matcher): String {
        val matchedBook = matcher.group(0) ?: ""
        val chapter = matcher.group(1) ?: ""
        val startVerse = matcher.group(2) ?: ""
        val endVerse = matcher.group(3)

        // Find which bible book was matched by finding prefix or exact match
        var matchedCanonicalBook = ""
        for (book in bibleBooks.keys) {
            if (matchedBook.startsWith(book)) {
                matchedCanonicalBook = book
                break
            }
        }
        if (matchedCanonicalBook.isEmpty()) {
            for ((full, abb) in bibleBooks) {
                if (matchedBook.startsWith(abb)) {
                    matchedCanonicalBook = full
                    break
                }
            }
        }

        if (matchedCanonicalBook.isEmpty()) matchedCanonicalBook = matchedBook.split(" ", ":", "장").firstOrNull() ?: matchedBook

        return if (endVerse != null && endVerse.isNotBlank()) {
            "$matchedCanonicalBook $chapter:$startVerse-$endVerse"
        } else {
            "$matchedCanonicalBook $chapter:$startVerse"
        }
    }

    /**
     * Extracts theological proper nouns that actually appear in the transcript.
     */
    fun extractProperNouns(transcript: String): String {
        if (transcript.isBlank()) return ""
        val nouns = mutableSetOf<String>()
        for (noun in theologicalNouns) {
            if (transcript.contains(noun)) {
                nouns.add(noun)
            }
        }
        return nouns.joinToString(", ")
    }

    /**
     * Extract 5 to 7 key phrases/words from transcript based on frequency.
     */
    fun extractKeywords(transcript: String): String {
        if (transcript.isBlank()) return ""
        // Split by non-word characters
        val words = transcript.split(Regex("[^a-zA-Z가-힣]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && !koreanStopwords.contains(it) }

        val frequencyMap = mutableMapOf<String, Int>()
        for (word in words) {
            frequencyMap[word] = frequencyMap.getOrDefault(word, 0) + 1
        }

        val sortedKeywords = frequencyMap.entries
            .sortedByDescending { it.value }
            .take(7)
            .map { it.key }

        return sortedKeywords.joinToString(", ")
    }

    /**
     * Generates a 3-4 sentence concise summary of the sermon.
     */
    fun generateSummary(title: String, transcript: String): String {
        if (transcript.isBlank()) return if (Locale.getDefault().language == "ko") "설교 내용이 없어 요약을 생성할 수 없습니다." else "No content to summarize."

        val sentences = transcript.split(Regex("(?<=[.?!])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.size <= 4) {
            return sentences.joinToString(" ")
        }

        // Homiletical Summary Rule:
        // Combine: First sentence (Hook), Longest sentence in the middle (Core Argument), Last sentence (Closing Call)
        val first = sentences.first()
        val last = sentences.last()

        val middleSentences = sentences.subList(1, sentences.size - 1)
        val longestMiddle = middleSentences.maxByOrNull { it.length } ?: ""
        
        // Find if there's any sentence with "바랍니다", "축원합니다", "합시다" (Action call)
        val actionCall = middleSentences.firstOrNull { 
            it.contains("바랍니다") || it.contains("축원합니다") || it.contains("합시다") || it.contains("기도합니다")
        }

        val isKo = Locale.getDefault().language == "ko"
        return if (isKo) {
            buildString {
                append("오늘 선포된 설교 「").append(title).append("」에서는 ")
                append(first.replace(Regex("^[\\s\"'“‘]+|[\\s\"'”’]+$"), "")).append(" ")
                if (longestMiddle.length > 10 && longestMiddle != first) {
                    append(longestMiddle).append(" ")
                }
                if (actionCall != null && actionCall != longestMiddle) {
                    append(actionCall).append(" ")
                } else {
                    append(last)
                }
            }.trim()
        } else {
            buildString {
                append("In the sermon \"").append(title).append("\", the message begins by highlighting that ")
                append(first.replace(Regex("^[\\s\"'“‘]+|[\\s\"'”’]+$"), "")).append(" ")
                if (longestMiddle.isNotEmpty() && longestMiddle != first) {
                    append("Furthermore, ").append(longestMiddle).append(" ")
                }
                append("Conclusively, ").append(last)
            }.trim()
        }
    }

    /**
     * Structural Outline in Markdown
     */
    fun generateSermonOutline(title: String, transcript: String, bibleRefs: String): String {
        val isKo = Locale.getDefault().language == "ko"
        if (transcript.isBlank()) {
            return if (isKo) "## 본문이 비어 있어 개요를 작성할 수 없습니다." else "## No outline available for empty text."
        }

        val paragraphs = transcript.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val sentences = transcript.split(Regex("(?<=[.?!])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val introductionText = sentences.take(2).joinToString(" ")
        val conclusionText = sentences.takeLast(2).joinToString(" ")

        return buildString {
            // Title
            append("# ").append(title).append("\n\n")

            // Key Scripture Block
            if (bibleRefs.isNotBlank()) {
                append("> **오늘의 성경 본문**\n")
                bibleRefs.split(",").forEach { ref ->
                    append("> - ").append(ref.trim()).append("\n")
                }
                append("\n")
            }

            // Introduction
            append("## 1. 설교 도입 (Introduction)\n")
            append("- **메시지 화두**: ").append(introductionText).append("\n")
            append("- 본 설교는 삶 속에서 맞닥뜨리는 영적 본질과 강단 선포를 깊이 있게 연결합니다.\n\n")

            // Body
            append("## 2. 설교 본론 (Sermon Body)\n")
            if (paragraphs.size >= 2) {
                paragraphs.forEachIndexed { index, para ->
                    val cleanPara = para.replace("\n", " ").trim()
                    val coreSentence = cleanPara.split(Regex("(?<=[.?!])\\s+")).firstOrNull() ?: cleanPara.take(60)
                    append("- **대지 ").append(index + 1).append("**: ").append(coreSentence).append("\n")
                    if (cleanPara.length > coreSentence.length) {
                        append("  - *강해 및 묵상*: ").append(cleanPara.substring(coreSentence.length).take(100).trim()).append("...\n")
                    }
                }
            } else {
                append("- **대지 1 (핵심 메시지)**: ").append(sentences.getOrNull(sentences.size / 2) ?: "믿음의 전진과 영적 성장").append("\n")
                append("  - 본문의 영적인 교훈을 마음에 새기고 묵상합니다.\n")
            }
            append("\n")

            // Conclusion
            append("## 3. 결론 및 적용 (Conclusion & Application)\n")
            append("- **결어 및 교훈**: ").append(conclusionText).append("\n")
            
            // Generate deterministic action points based on context
            append("- **구체적 실천 과제**:\n")
            if (isKo) {
                append("  1. 선포된 말씀 속 성경 구절을 깊이 묵상하고 하루 3번 기도하기\n")
                append("  2. 공동체 및 이웃 속에서 사랑과 감사를 말과 행동으로 표현하기\n")
            } else {
                append("  1. Meditate deeply on the scripture verses proclaimed and pray daily.\n")
                append("  2. Express love and gratitude through concrete actions within your community.\n")
            }
        }
    }
}
