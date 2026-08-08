package com.aechak.application.seller.usecase.result

import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import java.time.LocalDateTime

/** UseCase 반환 전용 모델 — 엔티티 반환 금지. 계좌번호는 여기서부터 마스킹(원문은 어드민 상세만). */
data class ApplicationResult(
    val applicationId: Long,
    val status: ApplicationStatus,
    val businessType: BusinessType,
    val businessName: String?,
    val businessRegNo: String?,
    val corpRegNo: String?,
    val representativeName: String?,
    val telesalesNumber: String?,
    val bankCode: String?,
    val accountNumberMasked: String?,
    val accountHolder: String?,
    val appliedAt: LocalDateTime,
    val submittedAt: LocalDateTime?,
    val decidedAt: LocalDateTime?,
    val rejectionReason: String?,
    val documents: List<ApplicationDocumentResult>,
) {
    companion object {
        /** accountNumber는 호출부가 복호화해 넘긴 평문 — 여기서 바로 마스킹돼 원문은 결과 모델에 실리지 않는다. */
        fun from(
            application: SellerApplication,
            accountNumber: String?,
        ): ApplicationResult =
            ApplicationResult(
                applicationId = application.id,
                status = application.status,
                businessType = application.businessType,
                businessName = application.businessName,
                businessRegNo = application.businessRegNo,
                corpRegNo = application.corpRegNo,
                representativeName = application.representativeName,
                telesalesNumber = application.telesalesNumber,
                bankCode = application.bankCode,
                accountNumberMasked = accountNumber?.let(::mask),
                accountHolder = application.accountHolder,
                appliedAt = application.createdAt,
                submittedAt = application.submittedAt,
                decidedAt = application.decidedAt,
                rejectionReason = if (application.status == ApplicationStatus.REJECTED) application.rejectionReason else null,
                documents = application.documents.map { ApplicationDocumentResult(it.documentType.name, it.updatedAt) },
            )

        /** 뒤 4자리 외 전부 마스킹 — 4자리 이하면 전부 가린다. */
        private fun mask(accountNumber: String): String =
            if (accountNumber.length <= 4) {
                "*".repeat(accountNumber.length)
            } else {
                "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
            }
    }
}

/** uploadedAt은 교체 시각까지 반영해야 하므로 updated_at을 쓴다. */
data class ApplicationDocumentResult(
    val documentType: String,
    val uploadedAt: LocalDateTime,
)
