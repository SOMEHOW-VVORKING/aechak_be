package com.aechak.application.inspection.facade

import com.aechak.application.inspection.port.ViolationSerializer
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.AiCallLedgerEntry
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.inspection.repository.AiCallLedgerRepository
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionVerdict
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * 판정 적용과 기한 정리의 트랜잭션 경계.
 *
 * 이 파사드는 노출 상태를 바꾸지 않는다. 판정을 받아 적기만 하고, 상품이 보이는지 아닌지는
 * 게이트를 켜는 단계에서 상품 도메인이 결정한다. 섀도로 먼저 돌려 판정 품질을 보는 동안
 * 구매자 화면이 흔들리지 않게 하려는 분리다.
 */
@Service
class InspectionFacade(
    private val inspections: ContentInspectionRepository,
    private val findings: InspectionFindingRepository,
    private val callLedger: AiCallLedgerRepository,
    private val violationSerializer: ViolationSerializer,
    @Value("\${inspection.wait-limit-minutes:10}") private val waitLimitMinutes: Long,
    @Value("\${inspection.expire-batch-size:200}") private val expireBatchSize: Int,
) : InspectionUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 판정을 적는다.
     *
     * 적기 전에 검수 행을 잠근다. 일반 조회로 읽고 쓰면 확인과 쓰기 사이에 기한 정리나
     * 또 다른 판정이 끼어들어 늦게 온 판정이 최신 상태를 덮는다.
     */
    @Transactional
    override fun applyVerdict(command: ApplyVerdictCommand) {
        val message = command.verdict
        val inspection = inspections.findByIdForUpdate(message.inspectionId)
        if (inspection == null) {
            log.warn("모르는 검수의 판정이 왔다. inspectionId={}", message.inspectionId)
            return
        }

        if (inspection.contentHash != message.contentHash) {
            // 그 사이 내용이 바뀌었다. 옛 내용의 판정으로 새 내용을 통과시키면 안 된다.
            log.info(
                "내용이 바뀐 뒤 도착한 판정을 버린다. inspectionId={} expected={} received={}",
                inspection.id,
                inspection.contentHash,
                message.contentHash,
            )
            return
        }

        if (!inspection.isUnjudged()) {
            log.debug("이미 판정이 적힌 검수다. inspectionId={}", inspection.id)
            return
        }

        if (inspection.targetType != message.targetType.toDomain()) {
            log.warn(
                "대상 종류가 어긋난 판정이 왔다. inspectionId={} stored={} received={}",
                inspection.id,
                inspection.targetType,
                message.targetType,
            )
            return
        }

        recordFindings(inspection.id, message)
        recordCalls(inspection.id, message)
        inspection.settle(
            judgment = message.verdict.toDomain(),
            promptVersion = message.promptVersion,
            reasonCode =
                message.violations
                    .firstOrNull()
                    ?.category
                    ?.name,
            reasonSummary =
                message.violations
                    .firstOrNull()
                    ?.quote
                    ?.take(REASON_SUMMARY_MAX),
            sellerNotice = message.sellerNotice,
            now = LocalDateTime.now(),
        )
        inspections.save(inspection)

        log.info(
            "판정 적용. inspectionId={} target={}:{} verdict={} violations={} unchecked={}",
            inspection.id,
            inspection.targetType,
            inspection.targetRef,
            message.verdict,
            message.violations.size,
            message.perspectives.count { it.outcome == ContractOutcome.UNCHECKED },
        )
    }

    /**
     * 기한이 지난 건을 미판정으로 닫는다.
     *
     * 대상은 판정을 받지 못한 건만이다. 조회 자체가 `judgment is null`로 거르므로
     * 모델이 보류시킨 상품은 기한이 지나도 풀리지 않는다.
     */
    @Transactional
    override fun expireOverdue(): Int {
        val now = LocalDateTime.now()
        val cutoff = now.minus(Duration.ofMinutes(waitLimitMinutes))
        val overdue = inspections.findExpired(cutoff, expireBatchSize)
        if (overdue.isEmpty()) return 0

        overdue.forEach { inspection ->
            inspection.expire(now)
            inspections.save(inspection)
            findings.upsert(
                InspectionFinding.of(
                    inspectionId = inspection.id,
                    perspective = InspectionPerspectiveKind.ALL,
                    outcome = PerspectiveOutcome.UNCHECKED,
                    skipReason = "WAIT_LIMIT_EXCEEDED",
                ),
            )
        }
        log.warn("기한이 지난 검수를 미판정으로 닫았다. count={} cutoff={}", overdue.size, cutoff)
        return overdue.size
    }

    /**
     * 호출마다 한 행씩 적는다.
     *
     * 판정 결과와 함께 들어오지만 판정의 전제는 아니다. 중복 전달에 같은 호출이 다시 와도
     * 조용히 넘기고, 적지 못해도 판정 적용을 막지 않는다.
     */
    private fun recordCalls(
        inspectionId: Long,
        message: ContentVerdictMessage,
    ) {
        if (message.calls.isEmpty()) return

        val purpose =
            when (message.targetType) {
                ContractTargetType.PRODUCT -> "PRODUCT_INSPECTION"
                ContractTargetType.REVIEW -> "REVIEW_INSPECTION"
            }
        val inserted =
            callLedger.insertIgnoringDuplicates(
                message.calls.map { call ->
                    AiCallLedgerEntry.of(
                        requestId = call.requestId,
                        callId = call.callId,
                        inspectionId = inspectionId,
                        purpose = purpose,
                        perspective = call.perspective.name,
                        modelId = call.modelId,
                        promptVersion = message.promptVersion,
                        inputTokens = call.inputTokens,
                        cacheReadInputTokens = call.cacheReadInputTokens,
                        cacheCreationInputTokens = call.cacheCreationInputTokens,
                        outputTokens = call.outputTokens,
                        costMicroUsd = call.costMicroUsd,
                        latencyMs = call.latencyMs,
                        outcome = call.outcome,
                    )
                },
            )
        val totalCost = message.calls.sumOf { it.costMicroUsd }
        val cacheRead = message.calls.sumOf { it.cacheReadInputTokens }
        if (cacheRead == 0) {
            // 캐시 미적중은 에러 없이 일어나고 비용만 올라간다. 접두부가 흔들렸다는 신호다.
            log.warn("판정에 프롬프트 캐시 적중이 없다. inspectionId={} calls={}", inspectionId, message.calls.size)
        }
        log.info(
            "호출 원장 적재. inspectionId={} calls={} inserted={} costMicroUsd={}",
            inspectionId,
            message.calls.size,
            inserted,
            totalCost,
        )
    }

    private fun recordFindings(
        inspectionId: Long,
        message: ContentVerdictMessage,
    ) {
        message.perspectives.forEach { result ->
            val perspective = result.perspective.toDomain()
            val own = message.violations.filter { it.perspective == result.perspective }
            findings.upsert(
                InspectionFinding.of(
                    inspectionId = inspectionId,
                    perspective = perspective,
                    outcome = result.outcome.toDomain(),
                    skipReason = result.skipReason,
                    violations = violationSerializer.toJson(own),
                ),
            )
        }
    }

    private fun ContractTargetType.toDomain(): InspectionTargetType =
        when (this) {
            ContractTargetType.PRODUCT -> InspectionTargetType.PRODUCT
            ContractTargetType.REVIEW -> InspectionTargetType.REVIEW
        }

    private fun InspectionVerdict.toDomain(): InspectionJudgment =
        when (this) {
            InspectionVerdict.PASS -> InspectionJudgment.PASS
            InspectionVerdict.FLAG -> InspectionJudgment.FLAG
            InspectionVerdict.FAILED -> InspectionJudgment.FAILED
        }

    private fun ContractOutcome.toDomain(): PerspectiveOutcome =
        when (this) {
            ContractOutcome.VIOLATION -> PerspectiveOutcome.VIOLATION
            ContractOutcome.CLEAR -> PerspectiveOutcome.CLEAR
            ContractOutcome.UNCHECKED -> PerspectiveOutcome.UNCHECKED
        }

    private fun InspectionPerspective.toDomain(): InspectionPerspectiveKind =
        when (this) {
            InspectionPerspective.ALL -> InspectionPerspectiveKind.ALL
            InspectionPerspective.DISPLAY_ADS -> InspectionPerspectiveKind.DISPLAY_ADS
            InspectionPerspective.CONDUCT_TRADE -> InspectionPerspectiveKind.CONDUCT_TRADE
            InspectionPerspective.RULE -> InspectionPerspectiveKind.RULE
        }

    companion object {
        private const val REASON_SUMMARY_MAX = 500
    }
}
