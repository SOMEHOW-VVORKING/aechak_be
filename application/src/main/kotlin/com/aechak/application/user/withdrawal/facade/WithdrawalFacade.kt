package com.aechak.application.user.withdrawal.facade

import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.user.withdrawal.service.WithdrawalService
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class WithdrawalFacade(
    private val withdrawalService: WithdrawalService,
    private val logoutUseCase: LogoutUseCase,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : WithdrawalUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    /** 안내 화면에 표시할 탈퇴 제한 사유를 조회 */
    @Transactional(readOnly = true)
    override fun checkWithdrawal(userId: Long): WithdrawalCheckResult = WithdrawalCheckResult(withdrawable = true)

    /** DB 변경을 커밋한 뒤 세션과 프로필 이미지 정리 */
    override fun withdraw(userId: Long) {
        val profileImageKey = tx.execute { withdrawalService.withdraw(userId) }
        revokeSessions(userId)
        deleteStoredProfileImage(userId, profileImageKey)
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
