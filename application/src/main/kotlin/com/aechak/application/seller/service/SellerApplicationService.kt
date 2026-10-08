package com.aechak.application.seller.service

import com.aechak.application.file.port.enums.FileType
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.seller.usecase.command.SaveDraftCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.application.ApplicationDocument
import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
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
    private val piiStringCodec: PiiStringCodec,
) {
    /** 임시저장 — 신청서 한 건을 재사용해 폼과 서류를 얹는다. */
    fun saveDraft(
        command: SaveDraftCommand,
        promotedKeys: Map<DocumentType, String>,
    ): SellerApplication {
        // 서류 없는 요청은 선검증을 거치지 않고, 거친 요청도 그 뒤 승격 왕복 동안 상태가 바뀔 수 있다
        requireNotSeller(command.userId)
        val application = findOrReopenDraft(command)
        updateForm(application, command)
        registerDocuments(application, promotedKeys)
        return application
    }

    /** 행 재사용 — 없으면 DRAFT 생성, REJECTED는 재작성 진입(reopen). */
    private fun findOrReopenDraft(command: SaveDraftCommand): SellerApplication {
        val application =
            sellerApplicationRepository.findByUserId(command.userId)
                ?: sellerApplicationRepository.save(SellerApplication.draft(command.userId, command.businessType))
        if (application.status == ApplicationStatus.REJECTED) {
            application.reopen()
        }
        return application
    }

    /** 폼 반영 — 계좌번호만 암호화해 넘긴다. SUBMITTED·APPROVED 수정 시도는 도메인 가드가 10101로 거른다. */
    private fun updateForm(
        application: SellerApplication,
        command: SaveDraftCommand,
    ) {
        application.updateDraft(
            businessType = command.businessType,
            businessName = command.businessName,
            businessRegNo = command.businessRegNo,
            corpRegNo = command.corpRegNo,
            representativeName = command.representativeName,
            telesalesNumber = command.telesalesNumber,
            bankCode = command.bankCode,
            // 빈 문자열은 null로 접는다 — 암호문은 항상 non-blank라, 여기서 거르지 않으면 제출 필수 검증이 뚫린다.
            accountNumberEnc = command.accountNumber?.takeIf { it.isNotBlank() }?.let(piiStringCodec::encrypt),
            accountHolder = command.accountHolder,
        )
    }

    /** 서류 부분 upsert — 승격을 마친 종류만 등록/교체하고, 미포함 종류는 유지한다. */
    private fun registerDocuments(
        application: SellerApplication,
        promotedKeys: Map<DocumentType, String>,
    ) {
        promotedKeys.forEach { (documentType, storageKey) ->
            application.registerDocument(
                ApplicationDocument.of(
                    documentType = documentType,
                    storageKey = storageKey,
                    contentType = contentTypeOf(storageKey),
                ),
            )
        }
    }

    fun getByUserId(userId: Long): SellerApplication =
        sellerApplicationRepository.findByUserId(userId)
            ?: throw BusinessException(SellerErrorCode.SELLER_APPLICATION_NOT_FOUND)

    /** 승격(S3 외부 호출) 전에 거절 사유를 미리 걸러 스토리지에 쓰레기를 남기지 않는다. */
    fun requireSavable(userId: Long) {
        requireNotSeller(userId)
        val status = sellerApplicationRepository.findByUserId(userId)?.status ?: return
        if (status != ApplicationStatus.DRAFT && status != ApplicationStatus.REJECTED) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
    }

    /** 이미 셀러인 계정은 신청 대상이 아니다 — 선검증과 저장이 함께 지키는 규칙. */
    private fun requireNotSeller(userId: Long) {
        if (sellerRepository.existsByUserId(userId)) {
            throw BusinessException(SellerErrorCode.ALREADY_SELLER)
        }
    }

    /** 제출 — 유형별 필수 정보·서류 세트 검증 통과 시에만 SUBMITTED로 전환한다. 대상은 내 신청서(없으면 10100). */
    fun submit(userId: Long) {
        val application = getByUserId(userId)
        requireSubmittable(application)
        application.submit()
    }

    /** design #6 매핑 — 유형이 바뀌어도 서류는 유지되므로 제출 시점의 유형 기준으로 재평가한다. */
    private fun requireSubmittable(application: SellerApplication) {
        val missingFields =
            requiredFieldsOf(application.businessType)
                .filter { it.read(application).isNullOrBlank() }
                .map { it.label }
        val registered = application.documents.map { it.documentType }.toSet()
        val missingDocuments =
            requiredDocumentsOf(application.businessType)
                .filterNot { it in registered }
                .map { it.label }
        if (missingFields.isEmpty() && missingDocuments.isEmpty()) return

        val parts = mutableListOf<String>()
        if (missingFields.isNotEmpty()) parts += "필수 정보가 누락됐습니다: ${missingFields.joinToString(", ")}"
        if (missingDocuments.isNotEmpty()) parts += "필수 서류가 누락됐습니다: ${missingDocuments.joinToString(", ")}"
        throw BusinessException(SellerErrorCode.REQUIRED_DOCUMENTS_MISSING, detail = parts.joinToString(" / "))
    }

    private fun requiredDocumentsOf(businessType: BusinessType): Set<DocumentType> =
        when (businessType) {
            BusinessType.PERSONAL_GENERAL -> {
                setOf(DocumentType.ID_CARD, DocumentType.BANKBOOK_COPY)
            }

            BusinessType.SOLE_PROPRIETORSHIP -> {
                setOf(
                    DocumentType.ID_CARD,
                    DocumentType.BANKBOOK_COPY,
                    DocumentType.BUSINESS_REGISTRATION,
                    DocumentType.TELESALES_REPORT,
                )
            }

            BusinessType.CORPORATE -> {
                DocumentType.entries.toSet()
            }
        }

    /** 필수 입력 항목 — label은 누락 안내 문구, read는 신청서에서 값을 꺼내는 방법. */
    private data class RequiredField(
        val label: String,
        val read: (SellerApplication) -> String?,
    )

    private fun requiredFieldsOf(businessType: BusinessType): List<RequiredField> {
        val common =
            listOf(
                RequiredField("대표자명") { it.representativeName },
                RequiredField("은행 코드") { it.bankCode },
                RequiredField("계좌번호") { it.accountNumberEnc },
                RequiredField("예금주") { it.accountHolder },
            )
        val business =
            listOf(
                RequiredField("상호명") { it.businessName },
                RequiredField("사업자등록번호") { it.businessRegNo },
            )
        val corporate = listOf(RequiredField("법인등록번호") { it.corpRegNo })
        return when (businessType) {
            BusinessType.PERSONAL_GENERAL -> common
            BusinessType.SOLE_PROPRIETORSHIP -> common + business
            BusinessType.CORPORATE -> common + business + corporate
        }
    }

    /** 승격을 통과한 키는 purpose 허용 타입의 확장자를 갖는다 — 미매칭은 발급 규칙 위반이라 500이 맞다. */
    private fun contentTypeOf(storageKey: String): String =
        FileType.entries.find { storageKey.endsWith(".${it.extension}") }?.mimeType
            ?: error("허용되지 않은 확장자의 승격 키 (key=$storageKey)")

    /** 표시(마스킹)·이체 등 사용 시점 복원 — 평문은 응답 조립 구간에만 존재한다. */
    fun decryptAccountNumber(application: SellerApplication): String? = application.accountNumberEnc?.let(piiStringCodec::decrypt)
}
