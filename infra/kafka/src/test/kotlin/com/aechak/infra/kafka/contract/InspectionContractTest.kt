package com.aechak.infra.kafka.contract

import com.aechak.infra.kafka.config.MessagingJacksonConfig
import com.aechak.message.Envelope
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionRequestedMessage
import com.aechak.message.inspection.InspectionTargetType
import com.aechak.message.inspection.InspectionVerdict
import com.aechak.message.inspection.PerspectiveOutcome
import com.aechak.message.inspection.ProductInspectionRequestedMessage
import com.aechak.message.inspection.ReviewInspectionRequestedMessage
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [단위] 파이썬 AI 서버와 공유하는 직렬화 계약을 고정한다.
 *
 * 표본 JSON을 리소스로 커밋해 두고 양쪽이 같은 바이트를 보게 만든다. 이 테스트가 깨지면
 * 계약이 바뀐 것이므로 AI 서버 쪽 미러도 같은 커밋에서 함께 고쳐야 한다.
 *
 * 이 테스트가 message 모듈이 아니라 여기 있는 이유는 그 모듈에 Jackson 의존이 없고
 * 공통 컨벤션이 모듈별 의존성 추가를 금지하기 때문이다.
 */
class InspectionContractTest {
    private val mapper: ObjectMapper = MessagingJacksonConfig().messagingObjectMapper()

    private val fixedAt: Instant = Instant.parse("2026-10-19T03:00:00Z")

    private val productRequest =
        ProductInspectionRequestedMessage(
            inspectionId = 1001L,
            productId = 42L,
            productVersionNo = 3,
            contentHash = "a".repeat(64),
            productName = "관절 영양제",
            description = "글루코사민 함유",
            optionNames = listOf("소형견 / 60정"),
            categoryPath = listOf("강아지", "건강관리", "영양제"),
            imageUrls = listOf("https://media.aechak.co.kr/products/01J.png"),
            occurredAt = fixedAt,
        )

    private val reviewRequest =
        ReviewInspectionRequestedMessage(
            inspectionId = 2002L,
            reviewId = 77L,
            productId = 42L,
            contentHash = "b".repeat(64),
            content = "우리 아이가 잘 먹어요",
            rating = 5,
            categoryPath = listOf("강아지", "건강관리", "영양제"),
            occurredAt = fixedAt,
        )

    /**
     * 표본과 **구조**를 비교한다. 바이트를 그대로 비교하지 않는 이유는 생성자 밖 프로퍼티의
     * 직렬화 순서가 명시적 정렬 없이 결정되어, Jackson이나 JDK가 올라가면 의미는 같은데
     * 순서만 달라지는 식으로 깨지기 때문이다. 필드가 늘거나 줄거나 이름이 바뀌는 것은
     * 구조 비교로도 그대로 잡힌다.
     */
    @Test
    fun `상품 검수 요청의 직렬화 모양이 고정된다`() {
        val json = mapper.writeValueAsString(productRequest)

        assertEquals(
            mapper.readTree(readFixture("product-inspection-requested.json")),
            mapper.readTree(json),
        )
    }

    @Test
    fun `리뷰 검수 요청의 직렬화 모양이 고정된다`() {
        val json = mapper.writeValueAsString(reviewRequest)

        assertEquals(
            mapper.readTree(readFixture("review-inspection-requested.json")),
            mapper.readTree(json),
        )
    }

    /**
     * 구조 비교가 느슨해지지 않도록 키 집합은 따로 못박는다.
     * 저쪽이 필드를 더하거나 빼면 백엔드 매퍼가 조용히 무시하므로 여기서 먼저 깨져야 한다.
     */
    @Test
    fun `요청 표본의 키 집합이 바뀌면 깨진다`() {
        val expected =
            setOf(
                "inspectionId",
                "productId",
                "productVersionNo",
                "contentHash",
                "productName",
                "description",
                "optionNames",
                "categoryPath",
                "imageUrls",
                "truncated",
                "schemaVersion",
                "occurredAt",
                "aggregateType",
                "eventId",
                "orderingKey",
                "targetRef",
                "targetType",
            )

        val actual = mapper.readTree(mapper.writeValueAsString(productRequest)).propertyNames().toSet()

        assertEquals(expected, actual)
    }

    @Test
    fun `판정 결과를 파이썬이 보낸 모양 그대로 역직렬화한다`() {
        val verdict = mapper.readValue(readFixture("content-verdict.json"), ContentVerdictMessage::class.java)

        assertEquals(1001L, verdict.inspectionId)
        assertEquals(InspectionTargetType.PRODUCT, verdict.targetType)
        assertEquals(InspectionVerdict.FLAG, verdict.verdict)
        assertEquals(ContentVerdictMessage.SCHEMA_VERSION, verdict.schemaVersion)
        assertEquals(1, verdict.violations.size)
        assertEquals("관절 건강에 도움을 줄 수 있는 글루코사민 함유", verdict.violations.first().suggestion)
        assertEquals(2, verdict.perspectives.size)
        assertEquals(
            PerspectiveOutcome.UNCHECKED,
            verdict.perspectives.first { it.perspective == InspectionPerspective.CONDUCT_TRADE }.outcome,
            "통과와 미검사를 구분하지 못하면 모델 장애로 못 본 것이 문제 없음으로 기록된다",
        )
        assertEquals(1, verdict.calls.size)
        assertEquals(
            verdict.calls.first().inputTokens + verdict.calls.first().cacheReadInputTokens +
                verdict.calls.first().cacheCreationInputTokens,
            1_200,
            "입력 토큰 셋을 나눠 받지 않으면 비용을 되살릴 수 없다",
        )
    }

    @Test
    fun `엔벨로프의 payload는 객체가 아니라 JSON 문자열이다`() {
        val envelopeJson = readFixture("verdict-envelope.json")
        val envelope = mapper.readValue(envelopeJson, Envelope::class.java)

        assertTrue(
            envelope.payload.trimStart().startsWith("{"),
            "payload를 객체로 미러하면 파이썬이 보낸 결과를 백엔드가 역직렬화하지 못한다",
        )
        val verdict = mapper.readValue(envelope.payload, ContentVerdictMessage::class.java)
        assertEquals("ContentVerdictMessage", envelope.eventType, "컨슈머 라우팅 기준은 계약 클래스명이다")
        assertEquals(1001L, verdict.inspectionId)
    }

    @Test
    fun `검수 요청은 전용 토픽으로 간다`() {
        assertEquals(InspectionRequestedMessage.AGGREGATE_TYPE, productRequest.aggregateType)
        assertEquals(InspectionRequestedMessage.AGGREGATE_TYPE, reviewRequest.aggregateType)
    }

    @Test
    fun `멱등키는 검수 건마다 다르고 반복 읽어도 같다`() {
        assertEquals("product-inspection-1001:requested", productRequest.eventId)
        assertEquals("review-inspection-2002:requested", reviewRequest.eventId)
        assertEquals(productRequest.eventId, productRequest.copy(productName = "다른 이름").eventId)
    }

    private fun readFixture(name: String): String =
        requireNonNull(javaClass.getResourceAsStream("/contract/$name"), name)
            .readAllBytes()
            .decodeToString()
            .trimEnd('\n')

    private fun <T> requireNonNull(
        value: T?,
        name: String,
    ): T = value ?: error("표본 리소스를 찾지 못했다: $name")
}
