package com.aechak.application.user.withdrawal.facade

import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.seller.usecase.SellerApplicationUseCase
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.seller.usecase.command.CancelApplicationCommand
import com.aechak.application.user.withdrawal.service.WithdrawalService
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalBlockReason
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalBlocker
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.user.error.UserErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class WithdrawalFacade(
    private val withdrawalService: WithdrawalService,
    private val logoutUseCase: LogoutUseCase,
    private val fileUseCase: FileUseCase,
    private val sellerUseCase: SellerUseCase,
    private val sellerApplicationUseCase: SellerApplicationUseCase,
    private val orderUseCase: OrderUseCase,
    transactionManager: PlatformTransactionManager,
) : WithdrawalUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    /** 안내 화면에 표시할 탈퇴 차단 사유를 조회 */
    @Transactional(readOnly = true)
    override fun checkWithdrawal(userId: Long): WithdrawalCheckResult = WithdrawalCheckResult(blockersOf(userId))

    /** DB 변경을 커밋한 뒤 세션과 프로필 이미지 정리 */
    override fun withdraw(userId: Long) {
        val profileImageKey = withdrawInTransaction(userId)
        revokeSessions(userId)
        deleteStoredProfileImage(userId, profileImageKey)
    }

    /** 차단 사유가 없으면 승인 전 입점 신청을 취소하고 탈퇴 처리 */
    private fun withdrawInTransaction(userId: Long): String? =
        try {
            tx.execute {
                // 셀러 판정보다 먼저 신청서를 읽어 둔다. 승인과 겹치면 커밋 때 낙관적 락 충돌이 난다
                sellerApplicationUseCase.cancel(CancelApplicationCommand(userId))
                if (blockersOf(userId).isNotEmpty()) throw BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED)
                withdrawalService.withdraw(userId)
            }
        } catch (e: OptimisticLockingFailureException) {
            // 충돌 뒤 다시 판정해서 사유가 생겼으면 탈퇴 차단, 다른 요청이 먼저 탈퇴를 커밋했으면 이미 탈퇴로 응답
            if (blockersOf(userId).isNotEmpty()) throw BusinessException(UserErrorCode.WITHDRAWAL_BLOCKED, e)
            if (withdrawalService.isWithdrawn(userId)) throw BusinessException(UserErrorCode.ALREADY_WITHDRAWN, e)
            throw e
        }

    /** 건수가 0인 사유는 빼고 enum 선언 순서로 반환 */
    private fun blockersOf(userId: Long): List<WithdrawalBlocker> =
        WithdrawalBlockReason.entries.mapNotNull { reason ->
            val count =
                when (reason) {
                    WithdrawalBlockReason.SELLER_ACCOUNT -> if (hasSellerAccount(userId)) 1 else 0
                    WithdrawalBlockReason.PAYMENT_IN_PROGRESS -> orderUseCase.countPendingPaymentOrderGroups(userId).toInt()
                }
            if (count > 0) WithdrawalBlocker(reason, count) else null
        }

    /** 퇴점(WITHDRAWN)하지 않은 셀러 행이 있으면 true */
    private fun hasSellerAccount(userId: Long): Boolean {
        val status = sellerUseCase.getSellerStatus(userId) ?: return false
        return status != SellerStatus.WITHDRAWN
    }

    /** 세션 삭제에 실패해도 이미 커밋된 탈퇴는 되돌리지 않음 */
    private fun revokeSessions(userId: Long) {
        runCatching { logoutUseCase.revokeAll(userId) }
            .onFailure { log.error("탈퇴 후 세션 무효화 실패, 수동 확인 필요 (userId={})", userId, it) }
    }

    /** 프로필 행을 지우기 전에 확보한 키로 스토리지 이미지를 삭제 */
    private fun deleteStoredProfileImage(
        userId: Long,
        key: String?,
    ) {
        if (key == null) return
        runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.USER_PROFILE)) }
            .onFailure { log.error("탈퇴 후 프로필 이미지 삭제 실패, 수동 확인 필요 (userId={}, key={})", userId, key, it) }
    }
}
