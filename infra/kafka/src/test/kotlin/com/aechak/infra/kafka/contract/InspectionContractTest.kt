package com.aechak.infra.kafka.contract

import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
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
import tools.jackson.databind.node.ObjectNode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * 요청 쪽에만 걸려 있던 키 집합 단언을 판정 쪽에도 건다. 없으면 저쪽이 필드를 더하거나 빼도
     * 매퍼가 조용히 무시해서, 계약 드리프트가 운영에서 판정이 이상해진 뒤에야 드러난다.
     *
     * 엔벨로프 payload까지 같이 보는 이유는 그쪽이 이스케이프된 JSON 문자열이라
     * 표본을 고칠 때 가장 자주 빠뜨리는 자리이기 때문이다.
     */
    @Test
    fun `판정 표본의 키 집합이 바뀌면 깨진다`() {
        val expected =
            setOf(
                "inspectionId",
                "targetType",
                "targetRef",
                "contentHash",
                "verdict",
                "perspectives",
                "violations",
                "sellerNotice",
                "calls",
                "criticOverturned",
                "promptVersion",
                "schemaVersion",
                "decidedAt",
            )

        VERDICT_FIXTURES.forEach { name ->
            assertEquals(expected, mapper.readTree(readFixture(name)).propertyNames().toSet(), name)
        }

        val envelope = mapper.readValue(readFixture("verdict-envelope.json"), Envelope::class.java)
        assertEquals(
            expected,
            mapper.readTree(envelope.payload).propertyNames().toSet(),
            "엔벨로프 payload는 이스케이프된 문자열이라 표본을 고칠 때 혼자 옛 모양으로 남는다",
        )

        val readBack = mapper.readValue(readFixture("content-verdict.json"), ContentVerdictMessage::class.java)
        assertEquals(
            expected,
            mapper.readTree(mapper.writeValueAsString(readBack)).propertyNames().toSet(),
            "계약 클래스가 표본과 어긋나면 표본만 고쳐 둔 것이다",
        )
    }

    /**
     * 모델 관점 집합의 이름이 도메인 열거에도 그대로 있는지 본다.
     *
     * 지표 집계의 SQL이 모델 관점 목록을 이 집합의 **이름**으로 만들고, 관점 기록 칼럼에는
     * 도메인 열거의 이름이 들어간다. 두 열거의 이름이 어긋나는 순간 게이트는 그 행을 증거로
     * 받는데 지표는 같은 건을 "모델이 보지 않았다"로 세고, 3단 검출율의 분모도 조용히 줄어든다.
     */
    @Test
    fun `모델 관점의 이름이 도메인 열거에 그대로 있다`() {
        ContentVerdictMessage.MODEL_PERSPECTIVES.forEach { perspective ->
            assertEquals(
                perspective.name,
                InspectionPerspectiveKind.valueOf(perspective.name).name,
                "계약 열거의 관점 이름이 도메인 열거에 없으면 지표의 SQL이 그 행을 모델 관점으로 세지 않는다",
            )
        }
    }

    /**
     * 선별이 통과시킨 건의 모양 — **건너뛴 관점마다 사유를 적은 행이 함께 온다.**
     *
     * 표본이 그 행들을 빼고 `[RULE/CLEAR, TRIAGE/CLEAR]` 둘로만 적고 있었고, 이 테스트는 그것을
     * "모델 판정 관점 없이 통과하는 것이 선별의 정상 경로다"로 못박고 있었다. 그런데 같은 레포의
     * 노출 게이트는 모델 관점 행이 비어 있는 통과를 증거 없는 통과로 다룬다 — 표본대로 보내면
     * 선별이 통과시킨 전건이 관리자 검토 큐로 간다. 선별이 관점 호출만 아끼고 사람 손은 그만큼 더
     * 쓰는 상태다. 생산자는 건너뛴 관점마다 사유 행을 남기므로 어긋난 것은 표본과 이 테스트였다.
     *
     * 그래서 표본을 게이트 술어에 직접 태운다. 두 테스트가 서로를 부정할 수 있었던 이유가 그
     * 묶음이 없었던 것이다.
     */
    @Test
    fun `선별이 통과시킨 판정을 역직렬화한다`() {
        val verdict = mapper.readValue(readFixture("content-verdict-triage.json"), ContentVerdictMessage::class.java)

        assertEquals(InspectionVerdict.PASS, verdict.verdict)
        assertEquals(
            setOf(
                InspectionPerspective.RULE,
                InspectionPerspective.TRIAGE,
                InspectionPerspective.DISPLAY_ADS,
                InspectionPerspective.CONDUCT_TRADE,
            ),
            verdict.perspectives.map { it.perspective }.toSet(),
            "건너뛴 모델 관점 행이 빠지면 노출 게이트가 이 통과를 증거 없는 통과로 다룬다",
        )
        assertEquals(
            PerspectiveOutcome.CLEAR,
            verdict.perspectives.first { it.perspective == InspectionPerspective.TRIAGE }.outcome,
        )
        verdict.perspectives
            .filter { it.perspective in ContentVerdictMessage.MODEL_PERSPECTIVES }
            .forEach { row ->
                assertEquals(PerspectiveOutcome.UNCHECKED, row.outcome, "선별이 건너뛴 관점은 판정한 것이 아니다")
                assertEquals(
                    ContentVerdictMessage.TRIAGE_SKIP_REASON,
                    row.skipReason,
                    "사유가 없으면 호출이 죽어 못 본 것과 구분되지 않아 게이트가 통과를 받지 못한다",
                )
            }
        assertTrue(
            verdict.hasModelEvidence(),
            "표본이 노출 게이트의 증거 조건을 통과하지 못한다 — 이 모양을 보내면 선별 통과 전건이 관리자 큐로 간다",
        )
        assertEquals(
            InspectionPerspective.TRIAGE,
            verdict.calls.single().perspective,
            "선별 호출을 ALL로 적으면 원장에서 비평·수정문구와 한 덩어리가 되어 승급률을 셀 수 없다",
        )
        assertFalse(verdict.criticOverturned)
    }

    @Test
    fun `승급된 선별과 비평 뒤집힘을 역직렬화한다`() {
        val verdict =
            mapper.readValue(readFixture("content-verdict-triage-promoted.json"), ContentVerdictMessage::class.java)

        val triage = verdict.perspectives.first { it.perspective == InspectionPerspective.TRIAGE }
        assertEquals(
            PerspectiveOutcome.UNCHECKED,
            triage.outcome,
            "승급은 내용에 대한 판정을 내린 것이 아니라 더 보라고 넘긴 것이므로 CLEAR로 적으면 거짓이 된다",
        )
        assertEquals("PROMOTED", triage.skipReason)
        assertEquals(InspectionVerdict.PASS, verdict.verdict)
        assertTrue(verdict.criticOverturned, "뒤집힘을 일급 필드로 받지 않으면 깨끗한 통과와 구분되지 않는다")
        assertTrue(verdict.violations.isEmpty(), "비평이 모델 위반을 지우므로 위반 목록은 비어서 온다")
    }

    /**
     * 필드가 없는 판정도 계속 읽혀야 한다. 이 기본값이 깨지면 계약 버전을 올리지 않고
     * 필드를 더한 전제가 무너진다.
     */
    @Test
    fun `criticOverturned가 없는 판정은 false로 읽힌다`() {
        val stripped =
            (mapper.readTree(readFixture("content-verdict.json")) as ObjectNode)
                .without("criticOverturned")

        val verdict = mapper.readValue(mapper.writeValueAsString(stripped), ContentVerdictMessage::class.java)

        assertFalse(verdict.criticOverturned, "AI가 아직 안 보내는 동안 판정 전건이 격리 토픽으로 간다")
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

    companion object {
        /** 판정 표본 전부. 새 경로를 추가하면 여기에 넣어야 키 집합 단언이 그 표본도 본다 */
        private val VERDICT_FIXTURES =
            listOf(
                "content-verdict.json",
                "content-verdict-triage.json",
                "content-verdict-triage-promoted.json",
            )
    }
}
