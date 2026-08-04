package com.aechak.domain.seller.application

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "seller_applications",
    indexes = [
        Index(name = "ix_seller_applications_status_submitted_at", columnList = "status, submitted_at"),
        Index(name = "ix_seller_applications_business_reg_no", columnList = "business_reg_no"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_seller_applications_user_id", columnNames = ["user_id"])],
)
class SellerApplication protected constructor(
    userId: Long?,
    businessType: BusinessType,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    var seller: Seller? = null
        protected set

    var userId: Long? = userId
        protected set

    var decidedAdminId: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var businessType: BusinessType = businessType
        protected set

    @Column(length = 50)
    var telesalesNumber: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ApplicationStatus = ApplicationStatus.DRAFT
        protected set

    @Column(columnDefinition = "TEXT")
    var rejectionReason: String? = null
        protected set

    var submittedAt: LocalDateTime? = null
        protected set

    var decidedAt: LocalDateTime? = null
        protected set

    @Column(length = 100)
    var businessName: String? = null
        protected set

    @Column(length = 20)
    var businessRegNo: String? = null
        protected set

    @Column(length = 20)
    var corpRegNo: String? = null
        protected set

    @Column(length = 50)
    var representativeName: String? = null
        protected set

    @Column(length = 10)
    var bankCode: String? = null
        protected set

    @Column(length = 64)
    var accountNumber: String? = null
        protected set

    @Column(length = 50)
    var accountHolder: String? = null
        protected set

    @Version
    var version: Long = 0
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private val _documents: MutableList<ApplicationDocument> = mutableListOf()
    val documents: List<ApplicationDocument> get() = _documents.toList()

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private val _reviews: MutableList<ApplicationReview> = mutableListOf()
    val reviews: List<ApplicationReview> get() = _reviews.toList()

    /** DRAFT 필드 수정 — 임시저장(upsert)의 도메인 경로. 유형이 바뀌어도 서류는 유지하고 제출 시점에 재평가한다. */
    fun updateDraft(
        businessType: BusinessType,
        businessName: String?,
        businessRegNo: String?,
        corpRegNo: String?,
        representativeName: String?,
        telesalesNumber: String?,
        bankCode: String?,
        accountNumber: String?,
        accountHolder: String?,
    ) {
        requireDraft()
        this.businessType = businessType
        this.businessName = businessName
        this.businessRegNo = businessRegNo
        this.corpRegNo = corpRegNo
        this.representativeName = representativeName
        this.telesalesNumber = telesalesNumber
        this.bankCode = bankCode
        this.accountNumber = accountNumber
        this.accountHolder = accountHolder
    }

    /** 반려된 신청의 재작성 진입 — 이후는 일반 수정·제출 플로우를 재사용한다. 리뷰 이력·반려 사유는 남긴다. */
    fun reopen() {
        if (status != ApplicationStatus.REJECTED) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
        status = ApplicationStatus.DRAFT
    }

    /** 서류 등록 — 신청서는 서류 종류당 1장. 같은 종류가 있으면 교체한다(기존 행은 orphanRemoval로 삭제). */
    fun registerDocument(document: ApplicationDocument) {
        requireDraft()
        _documents.removeAll { it.documentType == document.documentType }
        _documents += document
    }

    fun submit() {
        if (status != ApplicationStatus.DRAFT) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
        status = ApplicationStatus.SUBMITTED
        submittedAt = LocalDateTime.now()
    }

    fun approve(reviewerAdminId: Long) {
        requireDecidable()
        _reviews += ApplicationReview.approved(reviewerAdminId)
        status = ApplicationStatus.APPROVED
        decidedAdminId = reviewerAdminId
        decidedAt = LocalDateTime.now()
    }

    fun reject(
        reviewerAdminId: Long,
        reason: String,
    ) {
        requireDecidable()
        if (reason.isBlank()) {
            throw BusinessException(SellerErrorCode.REJECTION_REASON_REQUIRED)
        }
        _reviews += ApplicationReview.rejected(reviewerAdminId, reason)
        status = ApplicationStatus.REJECTED
        rejectionReason = reason
        decidedAdminId = reviewerAdminId
        decidedAt = LocalDateTime.now()
    }

    private fun requireDraft() {
        if (status != ApplicationStatus.DRAFT) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
    }

    private fun requireDecidable() {
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.REVIEWING) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
    }

    companion object {
        fun draft(
            userId: Long?,
            businessType: BusinessType,
        ): SellerApplication = SellerApplication(userId, businessType)
    }
}
