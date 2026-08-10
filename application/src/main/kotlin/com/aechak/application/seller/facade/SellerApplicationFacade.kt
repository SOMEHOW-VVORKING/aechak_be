package com.aechak.application.seller.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.seller.service.SellerApplicationService
import com.aechak.application.seller.usecase.SellerApplicationUseCase
import com.aechak.application.seller.usecase.command.SaveDraftCommand
import com.aechak.application.seller.usecase.result.ApplicationResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.DocumentType
import com.aechak.domain.seller.error.SellerErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * SellerApplicationUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * saveDraft는 TransactionTemplate(프로그램적 경계)을 쓴다: 동시 최초 신청의 UNIQUE(user_id) 위반은
 * 커밋 시점 flush에서 터져 선언적 경계 안의 catch로는 잡을 수 없다 — execute 밖 캐치에서
 * 제약명을 보고 10104로 번역한다(UserFacade 닉네임 선례).
 */
@Service
class SellerApplicationFacade(
    private val sellerApplicationService: SellerApplicationService,
    private val userUseCase: UserUseCase,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : SellerApplicationUseCase {
    private val tx = TransactionTemplate(transactionManager)

    /** 승격(S3 외부 호출)은 저장 트랜잭션 밖 — 선검증 tx → 승격 → 저장 tx (프로필 이미지 선례). */
    override fun saveDraft(command: SaveDraftCommand): ApplicationResult {
        requirePhoneVerified(command.userId)
        // 승격은 되돌릴 수 없는 외부 쓰기라 올릴 서류가 있을 때만 미리 거른다. 저장 가부의 판정 자체는 저장 트랜잭션이 다시 한다.
        if (command.documents.isNotEmpty()) {
            tx.execute { sellerApplicationService.requireSavable(command.userId) }
        }
        val promotedKeys = promoteDocuments(command)
        try {
            tx.execute { sellerApplicationService.saveDraft(command, promotedKeys) }
        } catch (e: DataIntegrityViolationException) {
            translateConcurrentApply(e)
        }
        return getMe(command.userId)
    }

    private fun promoteDocuments(command: SaveDraftCommand): Map<DocumentType, String> =
        command.documents.associate { document ->
            val promoted =
                fileUseCase.promote(
                    PromoteFileCommand(
                        tmpKey = document.tmpKey,
                        userId = command.userId,
                        purpose = UploadPurpose.SELLER_DOCUMENT,
                    ),
                )
            document.documentType to promoted.key
        }

    @Transactional(readOnly = true)
    override fun getMe(userId: Long): ApplicationResult {
        val application = sellerApplicationService.getByUserId(userId)
        return ApplicationResult.from(application, sellerApplicationService.decryptAccountNumber(application))
    }

    @Transactional
    override fun submit(userId: Long) {
        sellerApplicationService.submit(userId)
    }

    /** 신청 전제 — 휴대폰 점유 인증. 타 도메인 상태라 UseCase 경유로만 읽는다. */
    private fun requirePhoneVerified(userId: Long) {
        if (!userUseCase.getMe(userId).isPhoneVerified) {
            throw BusinessException(SellerErrorCode.PHONE_VERIFICATION_REQUIRED)
        }
    }

    /** 커밋 시점 무결성 위반의 번역 — 제약명 기반 분기(다른 제약까지 10104로 오라벨하지 않는다). */
    private fun translateConcurrentApply(e: DataIntegrityViolationException): Nothing {
        val cause = e.mostSpecificCause.message.orEmpty()
        if (cause.contains(SellerApplication.UK_USER_ID)) {
            throw BusinessException(SellerErrorCode.APPLICATION_ALREADY_IN_PROGRESS, e)
        }
        throw e
    }
}
