package com.aechak.application.seller.service

import com.aechak.application.file.port.enums.FileType
import com.aechak.application.seller.usecase.command.SaveDraftCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.application.ApplicationDocument
import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.DocumentType
import com.aechak.domain.seller.application.repository.SellerApplicationRepository
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.stereotype.Service

/** 입점 신청 비즈니스 로직 보관함 — Facade에서만 호출된다. */
@Service
class SellerApplicationService(
    private val sellerApplicationRepository: SellerApplicationRepository,
    private val sellerRepository: SellerRepository,
) {
    /**
     * 행 재사용 저장 — 없으면 DRAFT 생성, REJECTED는 재작성 진입(reopen) 후 수정.
     * SUBMITTED·APPROVED 수정 시도는 도메인 가드(updateDraft)가 10101로 거른다.
     * 서류는 부분 upsert — 승격을 마친 종류만 등록/교체하고, 미포함 종류는 유지한다.
     */
    fun saveDraft(
        command: SaveDraftCommand,
        promotedKeys: Map<DocumentType, String>,
    ): SellerApplication {
        if (sellerRepository.existsByUserId(command.userId)) {
            throw BusinessException(SellerErrorCode.ALREADY_SELLER)
        }
        val application =
            sellerApplicationRepository.findByUserId(command.userId)
                ?: sellerApplicationRepository.save(SellerApplication.draft(command.userId, command.businessType))
        if (application.status == ApplicationStatus.REJECTED) {
            application.reopen()
        }
        application.updateDraft(
            businessType = command.businessType,
            businessName = command.businessName,
            businessRegNo = command.businessRegNo,
            corpRegNo = command.corpRegNo,
            representativeName = command.representativeName,
            telesalesNumber = command.telesalesNumber,
            bankCode = command.bankCode,
            accountNumber = command.accountNumber,
            accountHolder = command.accountHolder,
        )
        promotedKeys.forEach { (documentType, storageKey) ->
            application.registerDocument(
                ApplicationDocument.of(
                    documentType = documentType,
                    storageKey = storageKey,
                    contentType = contentTypeOf(storageKey),
                ),
            )
        }
        return application
    }

    fun getByUserId(userId: Long): SellerApplication =
        sellerApplicationRepository.findByUserId(userId)
            ?: throw BusinessException(SellerErrorCode.SELLER_APPLICATION_NOT_FOUND)

    /** 승격(S3 외부 호출) 전에 거절 사유를 미리 걸러 스토리지에 쓰레기를 남기지 않는다. */
    fun requireSavable(userId: Long) {
        if (sellerRepository.existsByUserId(userId)) {
            throw BusinessException(SellerErrorCode.ALREADY_SELLER)
        }
        val status = sellerApplicationRepository.findByUserId(userId)?.status ?: return
        if (status != ApplicationStatus.DRAFT && status != ApplicationStatus.REJECTED) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
    }

    /** 승격을 통과한 키는 purpose 허용 타입의 확장자를 갖는다 — 미매칭은 발급 규칙 위반이라 500이 맞다. */
    private fun contentTypeOf(storageKey: String): String =
        FileType.entries.find { storageKey.endsWith(".${it.extension}") }?.mimeType
            ?: error("허용되지 않은 확장자의 승격 키 (key=$storageKey)")
}
