package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.view.ViolationView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [단위] 저장된 위반 JSON을 읽는 계약을 고정한다.
 *
 * 깨지면 저장된 수정 제안이 조용히 빠지거나, 모양이 맞지 않는 행 하나 때문에 사유 조회가 실패한다.
 */
class JacksonViolationDeserializerTest {
    private val deserializer = JacksonViolationDeserializer()

    @Test
    fun `저장된 위반을 저장 순서대로 읽는다`() {
        val stored =
            """
            [
              {
                "category": "MEDICAL_CLAIM", "perspective": "DISPLAY_ADS", "spanStart": 30, "spanEnd": 36,
                "quote": "슬개골 완치", "suggestion": "슬개골 건강 관리에 도움을 줄 수 있는", "confidence": null
              },
              {
                "category": "EFFICACY_OVERCLAIM", "perspective": "DISPLAY_ADS", "spanStart": 0, "spanEnd": 7,
                "quote": "100% 회복", "suggestion": "개선에 도움을 줄 수 있습니다", "confidence": 0.9
              }
            ]
            """.trimIndent()

        assertEquals(
            listOf(
                ViolationView("MEDICAL_CLAIM", 30, "슬개골 완치", "슬개골 건강 관리에 도움을 줄 수 있는"),
                ViolationView("EFFICACY_OVERCLAIM", 0, "100% 회복", "개선에 도움을 줄 수 있습니다"),
            ),
            deserializer.fromJson(stored),
            "저장된 값과 순서를 그대로 읽어야 한다",
        )
    }

    @Test
    fun `값이 null인 필드를 null로 읽는다`() {
        val stored = """[{"category":"BANNED_INGREDIENT","spanStart":null,"quote":null,"suggestion":null}]"""

        assertEquals(
            listOf(ViolationView("BANNED_INGREDIENT", null, null, null)),
            deserializer.fromJson(stored),
            "규칙 단계 위반은 위치와 제안이 null로 저장된다",
        )
    }

    @Test
    fun `없는 필드를 null로 읽는다`() {
        assertEquals(
            listOf(ViolationView("SPAM", null, null, null)),
            deserializer.fromJson("""[{"category":"SPAM"}]"""),
            "category 외의 필드는 없어도 읽어야 한다",
        )
    }

    @Test
    fun `모르는 분류 값을 문자열 그대로 읽는다`() {
        assertEquals(
            "NEW_KIND",
            deserializer.fromJson("""[{"category":"NEW_KIND"}]""")?.single()?.category,
            "분류가 늘어도 목록 전체를 잃으면 안 된다",
        )
    }

    @Test
    fun `모르는 필드를 무시한다`() {
        assertEquals(
            listOf(ViolationView("SPAM", 3, "도배", null)),
            deserializer.fromJson("""[{"category":"SPAM","spanStart":3,"quote":"도배","riskScore":1}]"""),
            "메시지 계약에 필드가 늘어도 읽기가 깨지면 안 된다",
        )
    }

    @Test
    fun `빈 배열을 빈 목록으로 읽는다`() {
        assertEquals(emptyList(), deserializer.fromJson("[]"), "읽기 실패인 null과 구분해야 한다")
    }

    @Test
    fun `모양이 맞지 않으면 null을 돌려준다`() {
        val malformed =
            listOf(
                """{"foo":1}""",
                """[1,2]""",
                """"text"""",
                """not json""",
                """[{"quote":"a"}]""",
                """[{"category":null}]""",
                """[{"category":"SPAM","spanStart":"abc"}]""",
                """[null]""",
                """null""",
            )

        malformed.forEach { stored ->
            assertNull(deserializer.fromJson(stored), "예외 없이 null을 돌려줘야 한다: $stored")
        }
    }
}
