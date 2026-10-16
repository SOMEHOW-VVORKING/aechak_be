package com.aechak.api.review.moderation

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 금칙어 스캐너 단위 테스트. 정규화 우회, 별표 위치, 화이트리스트 경계, 겹침, 비율을 고정한다. */
class AhoCorasickProfanityScannerTest {
    private val scanner = AhoCorasickProfanityScanner()

    @Test
    fun `금칙어가 없으면 매치 없음이고 원문을 그대로 돌려준다`() {
        val result = scanner.scan("배송 빠르고 품질 좋아요")

        assertFalse(result.hasMatch)
        assertEquals("배송 빠르고 품질 좋아요", result.maskedContent)
    }

    @Test
    fun `금칙어를 원문 위치에 별표로 가린다`() {
        val result = scanner.scan("시발 배송은 좋아요")

        assertTrue(result.hasMatch)
        assertEquals("** 배송은 좋아요", result.maskedContent)
    }

    @Test
    fun `공개 기본 사전과 서비스 전용 사전을 함께 적용한다`() {
        assertTrue(scanner.scan("뒤질래").hasMatch)
        assertTrue(scanner.scan("fuck").hasMatch)
    }

    @Test
    fun `공개 사전에서 제외한 단독어가 포함된 정상 리뷰는 허용한다`() {
        val contents =
            listOf(
                "써 보지도 못했는데 상품이 망가져서 왔어요",
                "개선하고자 의견을 남겨요",
                "유모차 바퀴에 잘 맞아요",
                "질기지 않고 씹는 맛이 좋아요",
                "조금 작은 모자지만 예뻐요",
                "하드코어한 디자인은 아니에요",
            )

        contents.forEach { content ->
            val result = scanner.scan(content)
            assertFalse(result.hasMatch, content)
            assertEquals(content, result.maskedContent)
        }
    }

    @Test
    fun `공백이나 특수문자로 우회해도 잡는다`() {
        assertTrue(scanner.scan("시 발 최악이에요").hasMatch)
        assertTrue(scanner.scan("시*발 최악이에요").hasMatch)
    }

    @Test
    fun `NFD로 분해해 입력해도 잡는다`() {
        val decomposed = Normalizer.normalize("시발 최악이에요", Normalizer.Form.NFD)

        assertTrue(scanner.scan(decomposed).hasMatch)
    }

    @Test
    fun `연속인 화이트리스트 단어는 마스킹하지 않는다`() {
        val result = scanner.scan("시발점 근처예요")

        assertFalse(result.hasMatch)
        assertEquals("시발점 근처예요", result.maskedContent)
    }

    @Test
    fun `화이트리스트가 구분자를 가로질러 조립돼도 진짜 비속어는 잡는다`() {
        val result = scanner.scan("시발 점수가 낮아요")

        assertTrue(result.hasMatch)
        assertEquals("** 점수가 낮아요", result.maskedContent)
    }

    @Test
    fun `새끼손가락 같은 정상 복합어는 마스킹하지 않는다`() {
        val result = scanner.scan("새끼손가락에도 잘 맞아요")

        assertFalse(result.hasMatch)
        assertEquals("새끼손가락에도 잘 맞아요", result.maskedContent)
    }

    @Test
    fun `겹치는 금칙어는 덮인 글자를 합쳐 가린다`() {
        val result = scanner.scan("개새끼 최악")

        assertTrue(result.hasMatch)
        assertEquals("*** 최악", result.maskedContent)
    }

    @Test
    fun `비속어 비율이 높으면 매치 비율이 절반을 넘는다`() {
        val result = scanner.scan("시발 씨발 좆")

        assertTrue(result.hasMatch)
        assertTrue(result.matchedRatio > 0.5)
    }
}
