package com.aechak.application.seller.usecase.command

import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.application.enums.DocumentType

/** 필수는 businessType뿐 — 필수성 검증은 제출(10105)로 일원화하고 저장은 형식만 본다. */
data class SaveDraftCommand(
    val userId: Long,
    val businessType: BusinessType,
    val businessName: String?,
    val businessRegNo: String?,
    val corpRegNo: String?,
    val representativeName: String?,
    val telesalesNumber: String?,
    val bankCode: String?,
    val accountNumber: String?,
    val accountHolder: String?,
    val documents: List<DocumentCommand>,
) {
    /** 서류 부분 upsert — 포함한 종류만 등록/교체하고, 미포함 종류는 유지한다. */
    data class DocumentCommand(
        val documentType: DocumentType,
        val tmpKey: String,
    )
}
