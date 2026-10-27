package com.aechak.application.inspection.facade

import com.aechak.application.inspection.port.ViolationSerializer
import com.aechak.application.inspection.service.ProductReviewQueueService
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.AiCallLedgerEntry
import com.aechak.domain.inspection.ContentInspection
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * 판정 적용과 기한 정리의 트랜잭션 경계.
 *
 * 판정을 받아 적고, 같은 트랜잭션에서 노출 게이트까지 반영한다. 갈라 두면 판정은 적혔는데
 * 상품은 그대로인 상태가 남는다.
 *
 * 게이트가 실제로 움직이는지는 전용 스위치가 정하고 기본은 꺼짐이다. 판정을 쌓아 품질을
 * 보는 단계와 구매자 화면에 반영하는 단계를 나눌 수 있어야 한다.
 */
@Service
class InspectionFacade(
    private val inspections: ContentInspectionRepository,
    private val findings: InspectionFindingRepository,
    private val callLedger: AiCallLedgerRepository,
    private val violationSerializer: ViolationSerializer,
    private val reviewQueueService: ProductReviewQueueService,
    transactionManager: PlatformTransactionManager,
    @Value("\${inspection.wait-limit-minutes:10}") private val waitLimitMinutes: Long,
    @Value("\${inspection.expire-batch-size:200}") private val expireBatchSize: Int,
) : InspectionUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 후보 조회. 호출부에 트랜잭션이 있으면 합류한다 — 그 스냅샷으로 후보를 고른다 */
    private val scanTx = TransactionTemplate(transactionManager)

    /** 후보 한 건의 처리 경계. 바깥 트랜잭션에 합류하지 않는다 */
    private val rowTx =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /**
     * 판정을 적는다.
     *
     * 적기 전에 검수 행을 잠근다. 일반 조회로 읽고 쓰면 확인과 쓰기 사이에 기한 정리나
     * 또 다른 판정이 끼어들어 늦게 온 판정이 최신 상태를 덮는다.
     *
     * 격리 수준을 READ COMMITTED로 낮춘다. 게이트 반영이 상품 행을 잠근 뒤에 최신 검수와
     * 관리자 결정을 비잠금으로 읽는데, 기본 격리에서는 읽기 뷰가 이 트랜잭션의 첫 일관 읽기에서
     * 고정되어 잠금을 받은 뒤에 읽어도 그 사이 커밋된 것이 보이지 않는다.
     *
     * 이 선언은 이 트랜잭션을 여는 쪽일 때만 효력이 있다. 바깥에서 먼저 열고 들어오면 합류하는
     * 쪽의 격리 선언은 조용히 무시되므로, 운영 진입점도 같은 격리 수준을 선언해야 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        applyGate(inspection, modelVerified = modelVerified(message))

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
     *
     * **후보를 먼저 읽고 잠금은 행 단위로 잡는다.** 잠금 순서는 검수 행 → 상품 행으로 판정
     * 적용과 같다. 범위는 자기가 처리할 행까지다. 조건 조회를 잠금 조회로 두면 걸 인덱스가
     * 없어 검수 테이블 전체와 그 뒤 간극까지 잠기고, 그 간극이 다음 INSERT 자리라서 스윕 한
     * 번이 그 사이의 상품 등록·수정을 전부 세운다. 다른 상품을 만지는 셀러 쓰기와 교착하는
     * 것도 거기서 나온다.
     *
     * **후보 하나마다 트랜잭션을 따로 연다.** 순서를 맞추고 범위를 좁혀도 배치 전체를 한
     * 트랜잭션에 두면 둘이 남는다. 첫째, 처리를 끝낸 상품의 잠금을 배치가 끝날 때까지 쥔다.
     * 한 상품에 과기한 행이 둘 있을 수 있고 두 인스턴스의 스윕은 서로 다른 행을 집으므로,
     * 한쪽이 상품 Y를 들고 X를 기다리는 동안 다른 쪽이 X를 들고 Y를 기다리는 모양이 생긴다.
     * 둘째, 후보 한 건의 실패가 그 주기에 처리한 전부를 되돌린다. 둘 다 결과가 같다. 배치가
     * 롤백되며 사람이 볼 기회인 미판정 큐 등재까지 사라진다.
     *
     * 후보 조회는 스냅샷이라 잠근 뒤 기한 조건을 다시 본다. 후보를 고른 뒤 판정이 도착한
     * 행을 그대로 닫으면 멀쩡히 판정된 건이 미판정으로 덮인다.
     */
    override fun expireOverdue(): Int {
        val now = LocalDateTime.now()
        val waitLimit = Duration.ofMinutes(waitLimitMinutes)
        val cutoff = now.minus(waitLimit)
        val candidateIds = scanTx.execute { inspections.findExpiredCandidateIds(cutoff, expireBatchSize) }.orEmpty()
        if (candidateIds.isEmpty()) return 0

        var expired = 0
        candidateIds.forEach { candidateId ->
            val closed =
                runCatching { rowTx.execute { expireOne(candidateId, now, waitLimit) } == true }
                    .onFailure { log.warn("기한 정리 한 건이 실패했다. 다음 주기에 다시 집는다. inspectionId={}", candidateId, it) }
                    .getOrDefault(false)
            if (closed) expired += 1
        }
        if (expired > 0) log.warn("기한이 지난 검수를 미판정으로 닫았다. count={} cutoff={}", expired, cutoff)
        return expired
    }

    /** 후보 한 건을 닫는다. 닫았으면 참. 남이 들고 있거나 그 사이 판정이 도착했으면 거짓 */
    private fun expireOne(
        candidateId: Long,
        now: LocalDateTime,
        waitLimit: Duration,
    ): Boolean {
        // 남이 들고 있는 행은 비어 온다. 기다리면 두 스윕이 같은 묶음에서 서로를 세운다.
        val inspection = inspections.findByIdForUpdateSkipLocked(candidateId) ?: return false
        if (!inspection.isExpired(now, waitLimit)) return false

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
        // 미판정도 관리자 검토 큐로 보냄. 안 보내면 등록 직후 상품이 PENDING에 남음
        applyGate(inspection, modelVerified = false)
        return true
    }

    /**
     * 판정을 노출 상태와 관리자 검토 큐로 옮긴다.
     *
     * 넘기는 것은 위반 판정과 증거 있는 통과뿐이다. 판정을 받지 못한 건, 판정 과정이 실패한
     * 건, 모델이 그 내용을 읽은 적 없는 통과는 전부 "모델이 읽지 않았다"라는 한 가지 사실이라
     * 같은 자리에 둔다 — 상품도 큐도 건드리지 않는다. 모델이 보지 못한 것을 위반으로 다루면
     * 멀쩡한 상품이 사라져 모델 장애가 그대로 매출 장애가 되고, 사람이 풀 것도 확정할 것도
     * 없는 행을 큐에 쌓으면 장애마다 운영자가 수백 건을 떠안는다.
     *
     * 증거 없는 통과를 그냥 승인해 버릴 수도 없다. 위반 판정으로 반려되어 열린 행이 있는
     * 상품에 그 통과가 오면, 모델이 아무것도 읽지 못한 메시지가 상품을 되살리고 관리자가 볼
     * 행까지 닫는다. 여기서 걸러 내면 반려와 열린 행이 그대로 남아 관리자의 오탐 해제와
     * 셀러 쪽 창구가 살아 있다.
     *
     * 통과 판정에 노출을 되돌리는 경로를 둔 이유는 복구 수단이 필요하기 때문이다. 셀러가
     * 문구를 고쳐 다시 통과했을 때 되살아나지 않으면 한 번 걸린 상품을 영구히 되살릴 수 없다.
     */
    private fun applyGate(
        inspection: ContentInspection,
        modelVerified: Boolean,
    ) {
        // 리뷰는 규칙 기반 마스킹이 이미 돌고 있어 이번 범위가 아니다.
        if (inspection.targetType != InspectionTargetType.PRODUCT) return

        val latest = inspections.findLatestByTarget(InspectionTargetType.PRODUCT, inspection.targetRef)
        if (latest?.id != inspection.id) {
            // 옛 내용의 판정이 늦게 와서 새 내용에 내린 판정을 덮지 않게 함
            log.info("최신 검수가 아니라 노출에 반영하지 않는다. inspectionId={} latestId={}", inspection.id, latest?.id)
            return
        }

        val judgment = checkNotNull(inspection.judgment) { "판정이 적히지 않은 검수를 반영하려 했다. id=${inspection.id}" }
        // 판정 실패와 기한 만료는 증거 없는 통과와 같은 사실이라 같은 자리에 둔다
        val applicable =
            when (judgment) {
                InspectionJudgment.FLAG -> true
                InspectionJudgment.PASS -> modelVerified
                else -> false
            }
        if (!applicable) {
            log.info(
                "모델이 읽은 적 없는 판정이라 상품과 큐를 건드리지 않는다. inspectionId={} judgment={}",
                inspection.id,
                judgment,
            )
            return
        }

        reviewQueueService.applyJudgment(
            productId = inspection.targetRef,
            inspectionId = inspection.id,
            judgment = judgment,
            judgedAt = checkNotNull(inspection.settledAt),
        )
    }

    /**
     * 통과 판정을 뒷받침하는 증거가 있는지 가른다.
     *
     * 모델 관점 가운데 하나라도 결과를 낸 것이 있으면 모델이 그 내용을 읽었다. 위반을 낸
     * 관점도 증거로 센다 — 여기서 보는 것은 무엇을 찾았는지가 아니라 읽었는지다.
     *
     * 선별이 일부러 건너뛴 건은 결과가 없어도 증거로 센다. 선별이 통과로 가른 것은 판단이고
     * 장애가 아니라서, 그것까지 막으면 선별 계층의 비용 절감이 사라진다. 호출이 죽어 건너뛴
     * 것과 가르는 축은 사유뿐이라 사유가 규약이 된다. 모델 관점 행이 아예 없으면 무엇을
     * 봤는지 알 길이 없으므로 증거로 세지 않는다.
     */
    private fun modelVerified(message: ContentVerdictMessage): Boolean {
        val modelRows = message.perspectives.filter { it.perspective in MODEL_PERSPECTIVES }
        if (modelRows.any { it.outcome != ContractOutcome.UNCHECKED }) return true

        val triageCleared =
            message.perspectives.any {
                it.perspective == InspectionPerspective.TRIAGE && it.outcome == ContractOutcome.CLEAR
            }
        return triageCleared &&
            modelRows.isNotEmpty() &&
            modelRows.all { it.skipReason == TRIAGE_SKIP_REASON }
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
            InspectionPerspective.TRIAGE -> InspectionPerspectiveKind.TRIAGE
        }

    companion object {
        private const val REASON_SUMMARY_MAX = 500

        /** 모델이 내용을 읽고 결과를 내는 관점. RULE과 TRIAGE는 모델 판정이 아니라 제외한다 */
        private val MODEL_PERSPECTIVES =
            setOf(
                InspectionPerspective.ALL,
                InspectionPerspective.DISPLAY_ADS,
                InspectionPerspective.CONDUCT_TRADE,
            )

        // 선별이 모델 호출을 일부러 건너뛸 때 쓰는 사유. AI 서버와 맞춰 둔 값이다
        private const val TRIAGE_SKIP_REASON = "TRIAGE_CLEAR"
    }
}
