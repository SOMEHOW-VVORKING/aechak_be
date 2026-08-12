package com.aechak.api.seller.response

import com.aechak.application.seller.usecase.result.ApplicationResult
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import java.time.LocalDateTime

data class ApplicationResponse(
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
    val documents: List<ApplicationDocumentResponse>,
) {
    companion object {
        fun from(result: ApplicationResult): ApplicationResponse =
            ApplicationResponse(
                applicationId = result.applicationId,
                status = result.status,
                businessType = result.businessType,
                businessName = result.businessName,
                businessRegNo = result.businessRegNo,
                corpRegNo = result.corpRegNo,
                representativeName = result.representativeName,
                telesalesNumber = result.telesalesNumber,
                bankCode = result.bankCode,
                accountNumberMasked = result.accountNumberMasked,
                accountHolder = result.accountHolder,
                appliedAt = result.appliedAt,
                submittedAt = result.submittedAt,
                decidedAt = result.decidedAt,
                rejectionReason = result.rejectionReason,
                documents = result.documents.map { ApplicationDocumentResponse(it.documentType, it.uploadedAt) },
            )
    }
}

data class ApplicationDocumentResponse(
    val documentType: String,
    val uploadedAt: LocalDateTime,
)
