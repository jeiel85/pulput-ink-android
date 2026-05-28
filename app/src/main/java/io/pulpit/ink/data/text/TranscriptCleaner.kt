package io.pulpit.ink.data.text

/**
 * Deterministic, fully on-device text cleanup for Whisper transcripts.
 *
 * Passes — each fixes a class of error a small regex/heuristic can handle
 * safely without an LLM, dictionary, network, or API key:
 *
 *  1. whitespace normalization — collapse runs, strip per-line leading
 *     whitespace, normalize paragraph gaps, normalize CRLF
 *  2. repeated n-gram dedup of window 1..5 — kills Whisper's "감사합니다
 *     감사합니다 감사합니다" / "thank you thank you" hallucination loops
 *
 * Punctuation correction is intentionally NOT done: Whisper already emits
 * punctuation reliably, and aggressive rules produce more false positives
 * than they fix. Anything semantic (grammar correction, summarization,
 * paraphrase) is left to the OpenAI-gated path.
 */
object TranscriptCleaner {

    private val NEWLINE_RUN    = Regex("\\r\\n?|\\n")
    private val WHITESPACE_RUN = Regex("[ \\t\\u00A0]+")
    private val LEADING_SPACE  = Regex("(^|\\n)[ \\t\\u00A0]+")
    private val PARAGRAPH_GAP  = Regex("\\n{2,}")

    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        var s = normalizeWhitespace(raw)
        s = collapseRepeatedNGrams(s)
        return s.trim()
    }

    private fun normalizeWhitespace(s: String): String {
        return s
            .replace(NEWLINE_RUN, "\n")
            .replace(WHITESPACE_RUN, " ")
            .replace(LEADING_SPACE, "$1")
            .replace(PARAGRAPH_GAP, "\n\n")
            .trim()
    }

    /**
     * Collapse consecutively-repeated n-grams. Each paragraph is processed
     * independently so paragraph boundaries survive untouched. The smallest
     * matching window is preferred — that way `[a,a,a,a]` collapses to `[a]`
     * via n=1 instead of being kept as a 2-gram, and patterns like
     * `[A,B,A,B,A,B,A,B]` reduce to `[A,B]` instead of `[A,B,A,B]`.
     */
    internal fun collapseRepeatedNGrams(s: String): String {
        if (s.isBlank()) return s
        return s.split("\n\n").joinToString("\n\n") { collapseInParagraph(it) }
    }

    private fun collapseInParagraph(para: String): String {
        if (para.isBlank()) return para
        val tokens = para.split(' ').filter { it.isNotEmpty() }
        if (tokens.size < 2) return para

        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var collapsed = false
            // Smallest window first — see kdoc above.
            for (n in 1..6) {
                if (i + 2 * n > tokens.size) break
                val a = tokens.subList(i, i + n)
                val b = tokens.subList(i + n, i + 2 * n)
                if (a == b) {
                    out.addAll(a)
                    var j = i + 2 * n
                    while (j + n <= tokens.size && tokens.subList(j, j + n) == a) {
                        j += n
                    }
                    i = j
                    collapsed = true
                    break
                }
            }
            if (!collapsed) {
                out.add(tokens[i])
                i++
            }
        }
        return out.joinToString(" ")
    }
}
