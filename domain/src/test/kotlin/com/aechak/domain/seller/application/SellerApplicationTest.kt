package com.aechak.domain.seller.application

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.error.SellerErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약 — 신청서 상태 가드·재작성 전이·서류 교체 의미론.
 * 깨지면 제출된 신청서가 수정되거나, 반려 후 재작성이 막히거나, 서류가 종류당 여러 장 쌓인다.
 */
class SellerApplicationTest {
    private fun draft() = SellerApplication.draft(userId = 1L, businessType = BusinessType.PERSONAL_GENERAL)

    private fun rejected() =
        draft().apply {
            submit()
            reject(reviewerAdminId = 10L, reason = "서류 미비")
        }

    private fun idCard(storageKey: String = "sellers/docs/id-1.png") = ApplicationDocument.of("ID_CARD", storageKey, "image/png")

    @Test
    fun `DRAFT에서 updateDraft로 신청 내용이 수정된다`() {
        val application = draft()

        application.updateDraft(
            businessType = BusinessType.SOLE_PROPRIETORSHIP,
            businessName = "애착상회",
            businessRegNo = "1234567890",
            corpRegNo = null,
            representativeName = "김운경",
            telesalesNumber = "2026-서울강남-01234",
            bankCode = "004",
            accountNumber = "enc:abc",
            accountHolder = "김운경",
        )

        assertEquals(BusinessType.SOLE_PROPRIETORSHIP, application.businessType, "유형 변경도 DRAFT 수정에 포함된다")
        assertEquals("애착상회", application.businessName)
        assertEquals("1234567890", application.businessRegNo)
        assertEquals(ApplicationStatus.DRAFT, application.status, "수정은 상태를 바꾸지 않는다")
    }

    @Test
    fun `SUBMITTED 상태에서는 updateDraft가 거부된다`() {
        val application = draft().apply { submit() }

        val ex =
            assertFailsWith<BusinessException> {
                application.updateDraft(
                    businessType = BusinessType.PERSONAL_GENERAL,
                    businessName = null,
                    businessRegNo = null,
                    corpRegNo = null,
                    representativeName = null,
                    telesalesNumber = null,
                    bankCode = null,
                    accountNumber = null,
                    accountHolder = null,
                )
            }

        assertEquals(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED, ex.errorCode)
    }

    @Test
    fun `REJECTED에서 reopen하면 DRAFT로 돌아가고 리뷰 이력은 남는다`() {
        val application = rejected()

        application.reopen()

        assertEquals(ApplicationStatus.DRAFT, application.status)
        assertEquals(1, application.reviews.size, "반려 이력은 재작성 후에도 누적 보존된다")
        assertEquals("서류 미비", application.rejectionReason, "직전 반려 사유는 재작성 화면 안내용으로 유지된다")
    }

    @Test
    fun `REJECTED가 아니면 reopen이 거부된다`() {
        val ex = assertFailsWith<BusinessException> { draft().reopen() }

        assertEquals(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED, ex.errorCode)
    }

    @Test
    fun `reopen 후에는 수정과 재제출이 가능하다`() {
        val application = rejected().apply { reopen() }

        application.updateDraft(
            businessType = BusinessType.PERSONAL_GENERAL,
            businessName = null,
            businessRegNo = null,
            corpRegNo = null,
            representativeName = "김운경",
            telesalesNumber = null,
            bankCode = null,
            accountNumber = null,
            accountHolder = null,
        )
        application.submit()

        assertEquals(ApplicationStatus.SUBMITTED, application.status)
    }

    @Test
    fun `같은 종류 서류를 다시 등록하면 교체된다`() {
        val application = draft()

        application.registerDocument(idCard(storageKey = "sellers/docs/id-old.png"))
        application.registerDocument(idCard(storageKey = "sellers/docs/id-new.png"))

        assertEquals(1, application.documents.size, "서류는 종류당 1장만 남아야 한다")
        assertEquals("sellers/docs/id-new.png", application.documents.single().storageKey)
    }

    @Test
    fun `다른 종류 서류는 나란히 유지된다`() {
        val application = draft()

        application.registerDocument(idCard())
        application.registerDocument(ApplicationDocument.of("BANKBOOK_COPY", "sellers/docs/bank-1.png", "image/png"))

        assertEquals(2, application.documents.size)
    }

    @Test
    fun `SUBMITTED 상태에서는 서류 교체가 거부된다`() {
        val application = draft().apply { submit() }

        val ex = assertFailsWith<BusinessException> { application.registerDocument(idCard()) }

        assertEquals(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED, ex.errorCode)
    }
}
