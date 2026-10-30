package com.aechak.api.inspection

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import com.aechak.message.inspection.CallUsage
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionVerdict
import com.aechak.message.inspection.PerspectiveResult
import com.aechak.message.inspection.Violation
import com.aechak.message.inspection.ViolationCategory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 판정 적용 규약을 고정한다.
 *
 * 여기서 잡는 것은 판정 품질이 아니라 **늦게 온 판정과 중복 판정을 어떻게 다루는가**다.
 * 그 처리가 틀리면 옛 내용의 판정으로 새 내용이 통과하거나, 같은 위반이 쌓여 집계가 부풀려진다.
 */
class InspectionVerdictIntegrationTest : KafkaIntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var findings: InspectionFindingRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Test
    fun `판정을 적으면 관점별 기록과 함께 닫힌다`() {
        val inspection = givenQueued(productId = 42L, hash = "a".repeat(64))

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, InspectionVerdict.FLAG)))

        val reloaded = tx.execute { inspections.findById(inspection.id) }!!
        assertEquals(InspectionJudgment.FLAG, reloaded.judgment)
        assertEquals(InspectionProcessingState.SETTLED, reloaded.processingState)
        assertNotNull(reloaded.settledAt)
        assertEquals("치료 효능 표현을 고쳐 주세요.", reloaded.sellerNotice)

        val recorded = tx.execute { findings.findByInspectionId(inspection.id) }!!
        val byPerspective = recorded.associateBy { it.perspective }
        assertEquals(
            PerspectiveOutcome.VIOLATION,
            byPerspective[InspectionPerspectiveKind.DISPLAY_ADS]?.outcome,
        )
        assertEquals(
            PerspectiveOutcome.UNCHECKED,
            byPerspective[InspectionPerspectiveKind.CONDUCT_TRADE]?.outcome,
            "통과와 미검사를 뭉개면 모델이 보지 못한 것이 문제 없음으로 남는다",
        )
        assertEquals("RATE_LIMITED", byPerspective[InspectionPerspectiveKind.CONDUCT_TRADE]?.skipReason)
        assertNotNull(byPerspective[InspectionPerspectiveKind.DISPLAY_ADS]?.violations)
    }

    /**
     * 선별 관점과 뒤집힘 표시는 계약에만 더했고 AI는 아직 보내지 않는다. 매핑이 실제로
     * 한 행으로 내려가는지와 뒤집힘 표시가 판정 적용을 막지 않는지를 지금 못박아,
     * 선별 계층을 켤 때 이 자리를 다시 의심하지 않게 한다.
     */
    @Test
    fun `선별 관점과 비평 뒤집힘 표시를 받아도 판정은 그대로 적용된다`() {
        val inspection = givenQueued(productId = 52L, hash = "4".repeat(64))
        val promoted =
            verdictFor(inspection, InspectionVerdict.PASS).copy(
                perspectives =
                    listOf(
                        PerspectiveResult(
                            InspectionPerspective.TRIAGE,
                            ContractOutcome.UNCHECKED,
                            skipReason = "PROMOTED",
                        ),
                        PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.VIOLATION),
                    ),
                // 비평이 모델 위반을 지우고 통과로 바꾼 모양이다
                violations = emptyList(),
                sellerNotice = null,
                calls = listOf(triageCall(inspection.id)),
                criticOverturned = true,
            )

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(promoted))

        val reloaded = tx.execute { inspections.findById(inspection.id) }!!
        assertEquals(InspectionJudgment.PASS, reloaded.judgment, "뒤집힘 표시가 판정 적용을 막으면 안 된다")
        assertEquals(InspectionProcessingState.SETTLED, reloaded.processingState)

        val recorded = tx.execute { findings.findByInspectionId(inspection.id) }!!
        val triage = recorded.first { it.perspective == InspectionPerspectiveKind.TRIAGE }
        assertEquals(
            PerspectiveOutcome.UNCHECKED,
            triage.outcome,
            "승급은 내용을 판정한 것이 아니라 더 보라고 넘긴 것이므로 CLEAR로 적으면 거짓이 된다",
        )
        assertEquals("PROMOTED", triage.skipReason)

        val ledgerPerspective =
            tx.execute {
                db
                    .sql("select perspective from ai_call_ledger where inspection_id = :id")
                    .param("id", inspection.id)
                    .query(String::class.java)
                    .single()
            }
        assertEquals(
            "TRIAGE",
            ledgerPerspective,
            "선별 호출을 ALL로 적으면 비평·수정문구와 한 덩어리가 되어 승급률을 셀 수 없다",
        )
    }

    @Test
    fun `호출 원장에 토큰과 비용이 적힌다`() {
        val inspection = givenQueued(productId = 50L, hash = "2".repeat(64))

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, InspectionVerdict.FLAG)))

        val row =
            tx.execute {
                db
                    .sql(
                        """
                        select request_id, call_id, purpose, perspective, model_id,
                               input_tokens, cache_read_input_tokens, cache_creation_input_tokens,
                               output_tokens, cost_micro_usd, outcome
                        from ai_call_ledger where inspection_id = :id
                        """.trimIndent(),
                    ).param("id", inspection.id)
                    .query()
                    .listOfRows()
            }!!

        assertEquals(1, row.size, "호출 1건이 원장에 남아야 비용을 되살릴 수 있다")
        val entry = row.first()
        assertEquals("PRODUCT_INSPECTION", entry["purpose"])
        assertEquals("DISPLAY_ADS", entry["perspective"])
        assertEquals("claude-sonnet-5", entry["model_id"])
        assertEquals(3_840L, (entry["cost_micro_usd"] as Number).toLong())
        assertEquals(
            1_200,
            listOf("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens")
                .sumOf { (entry[it] as Number).toInt() },
            "입력 토큰을 셋으로 나눠 적지 않으면 캐시가 아낀 양을 알 수 없다",
        )
        assertNotEquals(entry["request_id"], entry["call_id"], "논리 요청과 실제 호출을 같은 키로 적으면 재시도 비용이 묻힌다")
    }

    @Test
    fun `같은 호출이 두 번 와도 원장에 한 번만 적힌다`() {
        val inspection = givenQueued(productId = 51L, hash = "3".repeat(64))
        val verdict = verdictFor(inspection, InspectionVerdict.FLAG)

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdict))
        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdict))

        val count =
            tx.execute {
                db
                    .sql("select count(*) from ai_call_ledger where inspection_id = :id")
                    .param("id", inspection.id)
                    .query(Int::class.java)
                    .single()
            }!!

        assertEquals(1, count, "재전달마다 쌓이면 비용 집계가 부풀려진다")
    }

    @Test
    fun `내용이 바뀐 뒤 도착한 판정은 버린다`() {
        val inspection = givenQueued(productId = 43L, hash = "b".repeat(64))

        val stale = verdictFor(inspection, InspectionVerdict.PASS).copy(contentHash = "c".repeat(64))
        inspectionUseCase.applyVerdict(ApplyVerdictCommand(stale))

        val reloaded = tx.execute { inspections.findById(inspection.id) }!!
        assertNull(
            reloaded.judgment,
            "옛 내용의 판정으로 새 내용을 통과시키면 수정으로 검수를 우회할 수 있다",
        )
    }

    @Test
    fun `같은 판정이 두 번 와도 한 번만 적힌다`() {
        val inspection = givenQueued(productId = 44L, hash = "d".repeat(64))
        val verdict = verdictFor(inspection, InspectionVerdict.FLAG)

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdict))
        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdict))

        val recorded = tx.execute { findings.findByInspectionId(inspection.id) }!!
        assertEquals(
            2,
            recorded.size,
            "관점 둘이므로 두 행이어야 한다. 네 행이면 재전달마다 누적되고 있다",
        )
    }

    @Test
    fun `모르는 검수의 판정은 조용히 버린다`() {
        val orphan = verdictFor(givenQueued(productId = 45L, hash = "e".repeat(64)), InspectionVerdict.PASS)

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(orphan.copy(inspectionId = 999_999L)))

        val recorded = tx.execute { findings.findByInspectionId(999_999L) }!!
        assertEquals(0, recorded.size)
    }

    @Test
    fun `기한이 지나도 판정이 난 건은 풀리지 않는다`() {
        val judged = givenQueued(productId = 46L, hash = "f".repeat(64))
        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(judged, InspectionVerdict.FLAG)))
        val unjudged = givenQueued(productId = 47L, hash = "0".repeat(64))
        backdateRequestedAt(unjudged.id, minutes = 30)
        backdateRequestedAt(judged.id, minutes = 30)

        val closed = inspectionUseCase.expireOverdue()

        assertEquals(1, closed, "판정이 난 건까지 풀면 기한만 지나면 통과하는 셈이 된다")
        val stillFlagged = tx.execute { inspections.findById(judged.id) }!!
        assertEquals(InspectionJudgment.FLAG, stillFlagged.judgment)
        val expired = tx.execute { inspections.findById(unjudged.id) }!!
        assertEquals(InspectionJudgment.UNJUDGED, expired.judgment, "통과가 아니라 미판정으로 닫아야 한다")
    }

    @Test
    fun `기한이 되지 않은 건은 건드리지 않는다`() {
        givenQueued(productId = 48L, hash = "1".repeat(64))

        val closed = inspectionUseCase.expireOverdue()

        assertEquals(0, closed)
    }

    private fun givenQueued(
        productId: Long,
        hash: String,
    ): ContentInspection =
        tx.execute {
            inspections.save(
                ContentInspection.queue(
                    targetType = InspectionTargetType.PRODUCT,
                    targetRef = productId,
                    contentHash = hash,
                    policyVersion = POLICY_VERSION,
                    targetVersionNo = 1,
                ),
            )
        }!!

    /** 요청 시각을 과거로 밀어 기한 경과를 만든다. 테스트가 10분을 기다릴 수는 없다 */
    private fun backdateRequestedAt(
        inspectionId: Long,
        minutes: Long,
    ) {
        tx.execute {
            db
                .sql("update content_inspections set requested_at = DATE_SUB(NOW(6), INTERVAL :m MINUTE) where id = :id")
                .param("m", minutes)
                .param("id", inspectionId)
                .update()
        }
    }

    private fun verdictFor(
        inspection: ContentInspection,
        verdict: InspectionVerdict,
    ): ContentVerdictMessage =
        ContentVerdictMessage(
            inspectionId = inspection.id,
            targetType = ContractTargetType.PRODUCT,
            targetRef = inspection.targetRef,
            contentHash = inspection.contentHash,
            verdict = verdict,
            perspectives =
                listOf(
                    PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.VIOLATION),
                    PerspectiveResult(
                        InspectionPerspective.CONDUCT_TRADE,
                        ContractOutcome.UNCHECKED,
                        skipReason = "RATE_LIMITED",
                    ),
                ),
            violations =
                listOf(
                    Violation(
                        category = ViolationCategory.MEDICAL_CLAIM,
                        perspective = InspectionPerspective.DISPLAY_ADS,
                        spanStart = 0,
                        spanEnd = 10,
                        quote = "관절염 치료에 효과",
                        suggestion = "관절 건강에 도움을 줄 수 있는",
                        confidence = null,
                    ),
                ),
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
            calls =
                listOf(
                    CallUsage(
                        requestId = "insp-${inspection.id}",
                        callId = "insp-${inspection.id}-1",
                        perspective = InspectionPerspective.DISPLAY_ADS,
                        modelId = "claude-sonnet-5",
                        inputTokens = 1_000,
                        cacheReadInputTokens = 200,
                        cacheCreationInputTokens = 0,
                        outputTokens = 180,
                        costMicroUsd = 3_840,
                        latencyMs = 1_340,
                        outcome = "SUCCESS",
                    ),
                ),
            promptVersion = "inspection-v1+$POLICY_VERSION",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    /** 선별 호출 한 건. 비용은 레포의 단가표(haiku 입력 1.0 / 출력 5.0, 캐시읽기 0.1배)로 맞춰 둔다 */
    private fun triageCall(inspectionId: Long): CallUsage =
        CallUsage(
            requestId = "insp-$inspectionId",
            callId = "insp-$inspectionId-triage-1",
            perspective = InspectionPerspective.TRIAGE,
            modelId = "claude-haiku-4-5-20251001",
            inputTokens = 300,
            cacheReadInputTokens = 1_200,
            cacheCreationInputTokens = 0,
            outputTokens = 100,
            costMicroUsd = 920,
            latencyMs = 410,
            outcome = "SUCCESS",
        )

    companion object {
        private const val POLICY_VERSION = "policy-v1"
    }
}
