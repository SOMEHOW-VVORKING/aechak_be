package com.aechak.application.inspection.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 셀러 이의 접수의 폭주 방지 정책.
 *
 * 트랜잭션 애너테이션이 없다. 호출부의 쓰기 트랜잭션 안에서 돌아야 하고, 그 규약은 같은
 * 패키지의 요청 발행·조회 서비스와 같다. 셀러센터가 스캔하는 쪽이 이 패키지다.
 *
 * 잠금 순서와 행 쓰기는 검토 큐 서비스가 한 자리에서 들고, 여기는 정책만 들어 그것을 부른다.
 * 정책 조회(셀러별 건수·상품별 기각 이력)는 상품 행 잠금을 쥔 **뒤에** 한다. 잠금 전에 세면
 * 같은 상품을 두 번 누른 요청 둘이 서로의 커밋을 보지 못하고 같은 분모를 읽어, 한 번 누르는
 * 것으로 상한이 두 칸 깎인다.
 *
 * 그래도 셀러 단위 상한은 직렬화되지 않는다. 잠그는 것이 상품 행이라, 서로 다른 상품 N건에
 * 동시에 요청을 쏘면 N개 트랜잭션이 전부 같은 분모를 읽고 통과한다 — 초과 폭은 1이 아니라
 * **그 순간 겹친 요청 수만큼**이다. 막으려면 분모를 직렬화할 지점이 필요한데, 쓸 수 있는
 * 후보가 둘이고 둘 다 지금은 비용이 더 크다. 셀러 행 잠금은 검수 쪽이 다른 BC(셀러)의 행을
 * 쓰기 잠금으로 집는 경로를 새로 만드는 일이고, 열린 이의 행을 (owner_id, status) 범위 잠금으로
 * 집는 쪽은 잠금 순서를 상품 행 -> 큐 범위로 하나 더 늘리면서 판정 경로가 같은 구간에 INSERT
 * 하는 것과 겹친다.
 *
 * 그래서 지금 막고 있는 것은 **상품 단위**다. 상품마다 열린 이의는 하나고(중복 접수는 40503),
 * 열린 행 하나에 접수할 수 있는 횟수에 상한이 있고, 기각 뒤에는 쿨다운이 걸린다. 그러면 한
 * 셀러가 열 수 있는 이의의 상한은 그 셀러가 가진 차단된 상품 수를 넘지 못하고, 그 상품들은
 * 이미 관리자 큐에 행을 가지고 있다. 폭주의 실질 상한이 거기라서 셀러 단위 상한은 2차
 * 방어선으로 둔다.
 */
@Service
class InspectionAppealService(
    private val reviewQueueService: ProductReviewQueueService,
    /** 셀러 한 명이 동시에 열어 둘 수 있는 이의 건수 */
    @Value("\${inspection.appeal.max-open-per-seller:3}") private val maxOpenPerSeller: Int,
    /** 이의가 기각된 뒤 같은 상품에 다시 제기할 수 없는 시간 */
    @Value("\${inspection.appeal.cooldown-hours:24}") private val cooldownHours: Long,
    /** 열린 검토 행 하나에 접수할 수 있는 횟수. 무효화를 거쳐 다시 접수한 것까지 센다 */
    @Value("\${inspection.appeal.max-attempts-per-item:3}") private val maxAttemptsPerItem: Int,
) {
    /**
     * 사유는 필수다. 빈 값을 형식 오류로 돌려주지 않고 전용 코드로 답하기 위해 nullable로 받는다.
     *
     * 접수는 노출을 바꾸지 않는다. 자격이 열리는 신호는 상품의 노출 상태가 아니라
     * 열린 검토 행이고, 그 판단은 검토 큐 서비스가 상품 행을 잠근 뒤에 한다.
     */
    fun submit(
        sellerId: Long,
        productId: Long,
        reason: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val trimmed = reason?.trim()
        if (trimmed.isNullOrEmpty()) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_REASON_REQUIRED)

        // 상품 행을 먼저 잠근다. 정책 조회가 잠금 뒤로 가야 같은 상품에 겹친 요청이 서로의
        // 커밋을 보고 같은 분모를 두 번 쓰지 않는다.
        val target = reviewQueueService.lockProductForAppeal(productId)
        val openState = reviewQueueService.findOpenAppealState(productId)
        // 상품 단위 판단이 셀러 단위 상한보다 먼저다. 순서를 뒤집으면 정원이 꽉 찬 셀러가 이미
        // 이의를 건 상품을 다시 누를 때 "이미 접수했다" 대신 "상한 초과"를 받는다.
        requireNotDuplicated(openState)
        requireAttemptsLeft(openState)
        requireWithinOpenLimit(sellerId)
        requireCooldownPassed(productId, now)
        reviewQueueService.appealBySeller(productId, target, trimmed, now)
    }

    private fun requireNotDuplicated(openState: OpenAppealState?) {
        if (openState?.alreadyAppealed == true) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_ALREADY_SUBMITTED)
        }
    }

    /**
     * 열린 행 하나에 접수할 수 있는 횟수를 끊는다.
     *
     * 무효화가 라이브 칼럼을 비우므로 중복 검사·셀러 정원·쿨다운 어느 것도 "고치고 또 접수"를
     * 세지 못한다. 세는 것은 행이 들고 있는 시도 횟수뿐이고, 그 숫자는 행이 닫힐 때만 사라진다 —
     * 행이 닫히는 것은 관리자가 결정했거나 고친 내용이 통과한 것이라 새 예산을 줄 근거가 된다.
     */
    private fun requireAttemptsLeft(openState: OpenAppealState?) {
        if ((openState?.attemptCount ?: 0) >= maxAttemptsPerItem) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_ATTEMPTS_EXHAUSTED)
        }
    }

    private fun requireWithinOpenLimit(sellerId: Long) {
        if (reviewQueueService.countOpenAppeals(sellerId) >= maxOpenPerSeller) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_LIMIT_EXCEEDED)
        }
    }

    /**
     * 기각된 이의를 곧바로 다시 올리는 것을 막는다.
     *
     * 기각 이력을 상품 단위로 본다. 행 단위로 보면 내용이 바뀔 때마다 큐 행이 갈려 쿨다운이
     * 걸리지 않는다.
     *
     * 끊는 범위는 "기각 뒤 재접수"까지다. 셀러가 내용을 고쳐 새 판정을 받으면 열린 행의 이의는
     * 무효가 되고 재접수가 열리는데, 그 반복을 끊는 것은 이 쿨다운이 아니라 행이 들고 있는
     * 시도 횟수다([requireAttemptsLeft]).
     */
    private fun requireCooldownPassed(
        productId: Long,
        now: LocalDateTime,
    ) {
        val lastRejectedAt = reviewQueueService.findLastAppealRejectedAt(productId) ?: return
        if (lastRejectedAt.plusHours(cooldownHours).isAfter(now)) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_COOLDOWN)
        }
    }
}
