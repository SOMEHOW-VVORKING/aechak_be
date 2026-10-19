package com.aechak.batch.order

import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 승인된 취소 클레임의 환불 집행. 승인은 상태만 바꾸므로 이 스케줄이 돌지 않으면 돈이 나가지 않는다.
 *
 * 커서를 두지 않는다 — 집행에 성공하면 그 건은 스캔 조건(승인 + 환불 미집행)에서 빠지고,
 * 실패한 건은 환불 실패로 기록돼 역시 빠진다. 다음 회차는 늘 처음부터 훑어도 같은 건을 다시 보지 않는다.
 * 그래서 한 회차가 한 페이지만 본다 — 적체는 다음 회차가 이어 받는다.
 *
 * 실제로 돈을 움직이므로 기본값은 꺼져 있다. dev의 PG 상점이 테스트 상점인지 확인한 뒤 켠다.
 */
@Component
@ConditionalOnProperty(name = ["order.claim.refund.enabled"], havingValue = "true")
class ClaimRefundSchedule(
    private val claimRefundUseCase: ClaimRefundUseCase,
    @Value("\${order.claim.refund.page-size:50}") private val pageSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.claim.refund.cron:0 * * * * *}", zone = "Asia/Seoul")
    fun run() {
        val candidates = claimRefundUseCase.findRefundPendingClaims(pageSize)
        if (candidates.isEmpty()) return
        candidates.forEach { candidate ->
            try {
                val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(candidate.claimPublicId))
                log.info(
                    "클레임 환불 집행. claimPublicId={}, 결과={}, 환불액={}원, 적립금복원={}원, 재고건너뜀={}건",
                    candidate.claimPublicId,
                    result.status,
                    result.refundAmount,
                    result.releasedPoint,
                    result.skippedStockItems,
                )
            } catch (e: Exception) {
                // 한 건의 실패가 회차를 멈추지 않게 건별로 격리한다. 실패 사유는 파사드가 이미 ERROR로 남긴다
                log.warn("클레임 환불 집행 실패 — 다음 건으로 넘어감. claimPublicId={}", candidate.claimPublicId, e)
            }
        }
        if (candidates.size >= pageSize) {
            log.warn("환불 집행 회차가 페이지 상한에 걸림 — 남은 건은 다음 회차가 이어 간다. 처리={}건", candidates.size)
        }
    }
}
