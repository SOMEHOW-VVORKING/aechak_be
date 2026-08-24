package com.aechak.seller.seller.request

import com.aechak.application.seller.usecase.command.SaveDraftCommand
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.application.enums.DocumentType
import com.aechak.seller.seller.request.validation.BusinessRegNo
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 임시저장 요청 — 필수는 businessType뿐(필수성 검증은 제출 10105 일원화), 여기선 형식만 본다. */
data class SaveDraftRequest(
    val businessType: BusinessType,
    @field:Size(max = 100, message = "상호명은 100자 이하여야 합니다.")
    val businessName: String? = null,
    @field:BusinessRegNo
    val businessRegNo: String? = null,
    @field:Size(max = 20, message = "법인등록번호는 20자 이하여야 합니다.")
    val corpRegNo: String? = null,
    @field:Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
    val representativeName: String? = null,
    @field:Size(max = 50, message = "통신판매업 신고번호는 50자 이하여야 합니다.")
    val telesalesNumber: String? = null,
    @field:Size(max = 10, message = "은행 코드는 10자 이하여야 합니다.")
    val bankCode: String? = null,
    @field:Size(max = 64, message = "계좌번호는 64자 이하여야 합니다.")
    val accountNumber: String? = null,
    @field:Size(max = 50, message = "예금주는 50자 이하여야 합니다.")
    val accountHolder: String? = null,
    @field:Valid
    val documents: List<DocumentItem> = emptyList(),
) {
    /** 서류 부분 upsert — 같은 종류를 한 요청에 두 번 실으면 형식 위반이다. */
    @get:AssertTrue(message = "같은 종류의 서류는 한 번만 등록할 수 있습니다.")
    val documentTypesUnique: Boolean
        get() = documents.map { it.documentType }.toSet().size == documents.size

    data class DocumentItem(
        val documentType: DocumentType,
        @field:NotBlank(message = "tmpKey는 필수입니다.")
        val tmpKey: String,
    )

    fun toCommand(userId: Long): SaveDraftCommand =
        SaveDraftCommand(
            userId = userId,
            businessType = businessType,
            businessName = businessName,
            businessRegNo = businessRegNo,
            corpRegNo = corpRegNo,
            representativeName = representativeName,
            telesalesNumber = telesalesNumber,
            bankCode = bankCode,
            accountNumber = accountNumber,
            accountHolder = accountHolder,
            documents = documents.map { SaveDraftCommand.DocumentCommand(it.documentType, it.tmpKey) },
        )
}
