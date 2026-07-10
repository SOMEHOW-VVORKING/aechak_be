package com.aechak.domain.seller.application

import com.aechak.common.error.BusinessException
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
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.application.enums.ApplicationStatus

@Entity
@Table(name = "seller_applications")
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
    val businessType: BusinessType = businessType

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

    @Column
    var appliedAt: LocalDateTime? = LocalDateTime.now()
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

    fun addDocument(document: ApplicationDocument) {
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

    fun reject(reviewerAdminId: Long, reason: String) {
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

    private fun requireDecidable() {
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.REVIEWING) {
            throw BusinessException(SellerErrorCode.APPLICATION_STATUS_TRANSITION_NOT_ALLOWED)
        }
    }

    companion object {
        fun draft(userId: Long?, businessType: BusinessType): SellerApplication {
            return SellerApplication(userId, businessType)
        }
    }
}
