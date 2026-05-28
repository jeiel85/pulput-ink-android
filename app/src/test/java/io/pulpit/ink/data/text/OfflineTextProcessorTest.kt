package io.pulpit.ink.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rigorous JUnit validation test suite verifying natural language processing,
 * spacing/spelling grammar corrections, canonical Bible verse parsers,
 * and outline/summary generations on OfflineTextProcessor.
 */
class OfflineTextProcessorTest {

    @Test
    fun testKoreanGrammarAndSpacingCorrection() {
        // 1. Spacing check for auxiliary verb '수 있다'
        val spaceInput1 = "우리는 말씀대로 행할수있다 믿음이 충만해진다."
        val corrected1 = OfflineTextProcessor.correctTranscript(spaceInput1)
        assertTrue("수 있다 띄어쓰기 교정이 누락되었습니다.", corrected1.contains("행할 수 있다"))

        // 2. Spacing check for auxiliary noun '것 이다'
        val spaceInput2 = "이것이 오늘 선포될것같습니다 감사한일입니다."
        val corrected2 = OfflineTextProcessor.correctTranscript(spaceInput2)
        assertTrue("것 같다 띄어쓰기 교정이 누락되었습니다.", corrected2.contains("선포될 것 같습니다"))

        // 3. Spacing check for '때문 에'
        val spaceInput3 = "우리의 죄때문에 십자가를 지셨습니다."
        val corrected3 = OfflineTextProcessor.correctTranscript(spaceInput3)
        assertTrue("때문에 띄어쓰기 교정이 누락되었습니다.", corrected3.contains("죄 때문에"))

        // 4. Negation '안' vs '않' grammar checking
        val negInput1 = "이렇게 살면 안돼고 말씀을 들어야 합니다."
        val correctedNeg1 = OfflineTextProcessor.correctTranscript(negInput1)
        assertTrue("안 되고 맞춤법 교정이 누락되었습니다.", correctedNeg1.contains("안 되고"))

        val negInput2 = "기도하지 안는사람은 낙망합니다."
        val correctedNeg2 = OfflineTextProcessor.correctTranscript(negInput2)
        assertTrue("않는 맞춤법 교정이 누락되었습니다.", correctedNeg2.contains("않는사람은"))

        // 5. Common spelling typo corrections
        val typoInput = "오늘도 승리하시길 바래요 어떻게해서든 살아냅시다."
        val correctedTypo = OfflineTextProcessor.correctTranscript(typoInput)
        assertTrue("바라요 맞춤법 교정이 누락되었습니다.", correctedTypo.contains("바라요"))
        assertTrue("어떻게 해서든 맞춤법 교정이 누락되었습니다.", correctedTypo.contains("어떻게 해서든"))
    }

    @Test
    fun testBiblePassageParsingAndNormalization() {
        val transcriptWithVerses = """
            오늘 설교 본문은 창 1:1입니다. 
            또한 신약성경 요한복음 3장 16절 말씀과 마태복음 28:19-20 구절을 함께 묵상하겠습니다.
            마지막으로 계시록 22:21 말씀으로 마무리합니다.
        """.trimIndent()

        // 1. Extract canonical scripture list
        val extractedRefs = OfflineTextProcessor.extractBibleReferences(transcriptWithVerses)
        
        assertTrue("창세기 표준 구절 추출 실패", extractedRefs.contains("창세기 1:1"))
        assertTrue("요한복음 표준 구절 추출 실패", extractedRefs.contains("요한복음 3:16"))
        assertTrue("마태복음 범위 구절 추출 실패", extractedRefs.contains("마태복음 28:19-20"))
        assertTrue("요한계시록 표준 구절 추출 실패", extractedRefs.contains("요한계시록 22:21"))

        // 2. In-place transcript verse normalization check
        val normalizedTranscript = OfflineTextProcessor.correctTranscript(transcriptWithVerses)
        assertTrue("본문 내 요한복음 3:16 정형화 변환 실패", normalizedTranscript.contains("요한복음 3:16"))
        assertTrue("본문 내 마태복음 28:19-20 정형화 변환 실패", normalizedTranscript.contains("마태복음 28:19-20"))
    }

    @Test
    fun testTheologicalProperNounsAndKeywordsExtraction() {
        val transcript = """
            하나님은 아브라함과 다윗을 통해 구원의 언약을 주셨습니다. 
            예수 그리스도의 십자가 은혜는 복음의 핵심이며, 
            성령의 교통하심을 통해 우리의 믿음과 사랑이 자라나게 됩니다. 
            구원의 기쁨으로 예루살렘 성전에서 드리는 예배는 승리하는 삶의 비결입니다.
        """.trimIndent()

        // 1. Keyword extraction (TF frequency stopword filtered)
        val keywords = OfflineTextProcessor.extractKeywords(transcript)
        
        // Stops words like '은', '는', '통해' should not be present
        assertTrue("불용어가 걸러지지 않았습니다.", !keywords.contains("통해"))
        assertTrue("구원 키워드 추출 실패", keywords.contains("구원의") || keywords.contains("구원"))

        // 2. Proper theological nouns extraction
        val properNouns = OfflineTextProcessor.extractProperNouns(transcript)
        
        assertTrue("예수 추출 실패", properNouns.contains("예수"))
        assertTrue("하나님 추출 실패", properNouns.contains("하나님"))
        assertTrue("아브라함 추출 실패", properNouns.contains("아브라함"))
        assertTrue("다윗 추출 실패", properNouns.contains("다윗"))
        assertTrue("예루살렘 추출 실패", properNouns.contains("예루살렘"))
    }

    @Test
    fun testHomileticOutlineAndSummaryGeneration() {
        val transcript = """
            사랑하는 성도 여러분, 오늘 우리는 소망에 대해 묵상합니다. 
            고통과 두려움 속에 있을 때에도 주님의 은혜를 믿읍시다.
            성경 말씀 요한복음 3장 16절에 나와 있듯이 주님의 사랑은 언제나 우리와 함께 하십니다.
            낙심하지 말고 하루를 말씀 속에서 승리하며 나아갑시다.
            주님의 축복이 여러분에게 충만하길 축원합니다.
        """.trimIndent()

        val title = "소망의 인내와 승리"
        val refs = OfflineTextProcessor.extractBibleReferences(transcript)

        // 1. Summary Generation Check
        val summary = OfflineTextProcessor.generateSummary(title, transcript)
        assertTrue("요약이 올바르게 생성되지 않았습니다.", summary.startsWith("오늘 선포된 설교 「소망의 인내와 승리」에서는"))
        assertTrue("요약에 본문 첫 문장의 핵심 화두가 포함되지 않았습니다.", summary.contains("소망에 대해 묵상합니다"))

        // 2. Outline Generation Check
        val outline = OfflineTextProcessor.generateSermonOutline(title, transcript, refs)
        
        // Verify markdown headings and blockquotes
        assertTrue("마크다운 설교 제목 생성 오류", outline.contains("# 소망의 인내와 승리"))
        assertTrue("성경 인용 영역 오류", outline.contains("> - 요한복음 3:16"))
        assertTrue("도입 섹션 생성 오류", outline.contains("## 1. 설교 도입 (Introduction)"))
        assertTrue("본론 대지 생성 오류", outline.contains("## 2. 설교 본론 (Sermon Body)"))
        assertTrue("결론 및 적용 섹션 생성 오류", outline.contains("## 3. 결론 및 적용 (Conclusion & Application)"))
    }
}
