package com.aechak.batch.order

import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.command.AutoApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.result.AutoApprovableClaimResult
import com.aechak.application.order.claim.usecase.result.AutoApproveClaimStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 셀러가 응답 기한을 넘긴 취소 클레임의 자동승인과 환불 집행.
 *
 * 승인과 집행을 한 회차 안에서 이어 부르되 계약은 둘로 나뉘어 있다 — 승인은 상태만 바꾸는 짧은 트랜잭션이고,
 * 집행은 PG 호출을 가운데 둔 세 경계다. 집행은 환불 배치와 같은 유스케이스를 부르므로 선점 1행이 멱등 게이트인 것도 같다.
 * 승인만 되고 집행이 끊긴 건은 환불 배치가 이어 받는다.
 *
 * 커서를 두지 않는다 — 승인에 성공하면 그 건은 스캔 조건(미응답 + 기한 경과)에서 빠진다.
 * 그래서 한 회차가 한 페이지만 본다. 적체는 다음 회차가 이어 받는다.
 *
 * 환불 집행 스위치가 켜져 있을 때만 조립된다 — 자동승인이 그 스위치를 우회해 돈을 내보내면 안 된다.
 */
@Component
@ConditionalOnProperty(
    name = ["order.claim.refund.enabled", "order.claim.auto-approve.enabled"],
    havingValue = "true",
)
class ClaimAutoApproveSchedule(
    private val claimAutoApproveUseCase: ClaimAutoApproveUseCase,
    private val claimRefundUseCase: ClaimRefundUseCase,
    @Value("\${order.claim.auto-approve.page-size:50}") private val pageSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.claim.auto-approve.cron:0 10 * * * *}", zone = "Asia/Seoul")
    fun run() {
        val candidates = claimAutoApproveUseCase.findAutoApprovableClaims(pageSize)
        if (candidates.isEmpty()) return
        candidates.forEach { candidate ->
            try {
                approveAndRefund(candidate)
            } catch (e: OptimisticLockingFailureException) {
                // 같은 클레임을 셀러가 먼저 처리했다 — 다음 회차 스캔에서 빠지므로 재시도할 것이 없다
                log.debug("자동승인이 셀러 처리와 겹쳐 밀림. claimPublicId={}", candidate.claimPublicId, e)
            } catch (e: Exception) {
                // 한 건의 실패가 회차를 멈추지 않게 건별로 격리한다. 실패 사유는 각 파사드가 이미 남긴다
                log.warn("클레임 자동승인 실패 — 다음 건으로 넘어감. claimPublicId={}", candidate.claimPublicId, e)
            }
        }
        if (candidates.size >= pageSize) {
            log.warn("자동승인 회차가 페이지 상한에 걸림 — 남은 건은 다음 회차가 이어 간다. 처리={}건", candidates.size)
        }
    }

    private fun approveAndRefund(candidate: AutoApprovableClaimResult) {
        val approval = claimAutoApproveUseCase.autoApprove(AutoApproveClaimCommand(candidate.claimPublicId))
        if (approval.status != AutoApproveClaimStatus.APPROVED) {
            log.info("자동승인 대상이 아니라 건너뜀. claimPublicId={}", candidate.claimPublicId)
            return
        }
        log.info(
            "클레임 자동승인. claimPublicId={}, 응답기한={}",
            candidate.claimPublicId,
            candidate.autoApproveDueAt,
        )
        val refund = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(candidate.claimPublicId))
        log.info(
            "자동승인 건 환불 집행. claimPublicId={}, 결과={}, 환불액={}원, 적립금복원={}원, 재고건너뜀={}건",
            candidate.claimPublicId,
            refund.status,
            refund.refundAmount,
            refund.releasedPoint,
            refund.skippedStockItems,
        )
    }
}
