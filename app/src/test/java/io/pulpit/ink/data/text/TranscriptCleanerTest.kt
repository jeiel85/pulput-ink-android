package io.pulpit.ink.data.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {

    /* ---------------- whitespace normalization ---------------- */

    @Test fun collapsesIntralineWhitespace() {
        assertEquals("hello world", TranscriptCleaner.clean("hello     world"))
    }

    @Test fun stripsLeadingPerLineSpace() {
        assertEquals("first\n\nsecond", TranscriptCleaner.clean("   first\n\n   second"))
    }

    @Test fun normalizesParagraphGaps() {
        assertEquals("a\n\nb", TranscriptCleaner.clean("a\n\n\n\n\nb"))
    }

    @Test fun normalizesCrlf() {
        assertEquals("a\nb", TranscriptCleaner.clean("a\r\nb"))
    }

    @Test fun trimsOuterWhitespace() {
        assertEquals("hello world.", TranscriptCleaner.clean("   hello world.   "))
    }

    @Test fun handlesEmptyInput() {
        assertEquals("", TranscriptCleaner.clean(""))
        assertEquals("", TranscriptCleaner.clean("    "))
        assertEquals("", TranscriptCleaner.clean("\n\n\n"))
    }

    /* ---------------- n-gram dedup ---------------- */

    @Test fun collapsesUnigramHallucination() {
        assertEquals("very important", TranscriptCleaner.clean("very very very important"))
    }

    @Test fun collapsesBigramHallucination() {
        assertEquals("thank you everyone", TranscriptCleaner.clean("thank you thank you everyone"))
    }

    @Test fun collapsesTrigramHallucination() {
        assertEquals("this is good", TranscriptCleaner.clean("this is good this is good"))
    }

    @Test fun collapsesFourgramHallucination() {
        assertEquals(
            "we praise the lord",
            TranscriptCleaner.clean("we praise the lord we praise the lord")
        )
    }

    @Test fun collapsesFivegramHallucination() {
        assertEquals(
            "the kingdom of God is near",
            TranscriptCleaner.clean("the kingdom of God is near the kingdom of God is near")
        )
    }

    @Test fun collapsesKoreanUnigramHallucination() {
        assertEquals("감사합니다", TranscriptCleaner.clean("감사합니다 감사합니다 감사합니다"))
    }

    @Test fun collapsesKoreanMultiRepeats() {
        // 2-gram repeated 4 times — smallest-window heuristic must reduce to 1 copy
        assertEquals(
            "주님께 영광을",
            TranscriptCleaner.clean("주님께 영광을 주님께 영광을 주님께 영광을 주님께 영광을")
        )
    }

    @Test fun collapsesIdenticalTokenRunOfFour() {
        assertEquals("a", TranscriptCleaner.clean("a a a a"))
    }

    @Test fun preservesLegitConsecutiveDifferentWords() {
        assertEquals(
            "and the word was God.",
            TranscriptCleaner.clean("and the word was God.")
        )
    }

    @Test fun collapsesAcrossDifferentParagraphsIndependently() {
        // Dedup must not cross \n\n boundaries
        val input = "감사합니다 감사합니다\n\n아멘 아멘 아멘"
        assertEquals("감사합니다\n\n아멘", TranscriptCleaner.clean(input))
    }

    /* ---------------- punctuation preservation ---------------- */
    // No automatic punctuation addition; existing punctuation must be preserved.

    @Test fun preservesExistingPeriod() {
        assertEquals("우리는 기뻐합니다.", TranscriptCleaner.clean("우리는 기뻐합니다."))
    }

    @Test fun preservesExistingQuestionMark() {
        assertEquals("어디로 가야 하나요?", TranscriptCleaner.clean("어디로 가야 하나요?"))
    }

    @Test fun preservesExistingEllipsis() {
        assertEquals("아 그렇군요…", TranscriptCleaner.clean("아 그렇군요…"))
    }

    @Test fun doesNotAddPeriodToBareWord() {
        // Whisper already emits punctuation when speech has clear stops.
        // We deliberately do NOT invent it where it's missing.
        assertEquals("우리는 기뻐합니다", TranscriptCleaner.clean("우리는 기뻐합니다"))
    }

    /* ---------------- realistic combined cases ---------------- */

    @Test fun realisticKoreanSermonSnippet() {
        val raw = "   사랑하는 성도 여러분 사랑하는 성도 여러분   \n\n\n   오늘 우리는 본문에서   "
        assertEquals(
            "사랑하는 성도 여러분\n\n오늘 우리는 본문에서",
            TranscriptCleaner.clean(raw)
        )
    }

    @Test fun realisticWhisperPunctuationKept() {
        // Trailing commas attach to the last token so the 3-gram
        // [사랑하는, 성도, 여러분,] matches its repeat and collapses — desired
        // behaviour: Whisper's hallucinated repeat is removed while the comma
        // that was emitted by Whisper itself survives untouched.
        val raw = "  사랑하는 성도 여러분, 사랑하는 성도 여러분,  \n\n\n   오늘 우리는 본문에서 다음과 같이 읽었습니다.  "
        assertEquals(
            "사랑하는 성도 여러분,\n\n오늘 우리는 본문에서 다음과 같이 읽었습니다.",
            TranscriptCleaner.clean(raw)
        )
    }

    @Test fun preservesMultiParagraphStructure() {
        val raw = "첫 단락입니다.\n\n둘째 단락입니다.\n\n셋째 단락입니다."
        assertEquals(raw, TranscriptCleaner.clean(raw))
    }

    @Test fun blankParagraphsDropped() {
        val input = "first.\n\n\n\nsecond."
        assertEquals("first.\n\nsecond.", TranscriptCleaner.clean(input))
    }
}
