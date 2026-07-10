package com.aechak.domain.seller.application

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import com.aechak.domain.seller.application.enums.ReviewDecision

@Entity
@Table(name = "application_reviews")
class ApplicationReview protected constructor(
    reviewerAdminId: Long,
    decision: ReviewDecision,
    rejectionReason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val reviewerAdminId: Long = reviewerAdminId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val decision: ReviewDecision = decision

    @Column(length = 500)
    val rejectionReason: String? = rejectionReason

    @Column
    val reviewedAt: LocalDateTime? = LocalDateTime.now()

    companion object {
        fun approved(reviewerAdminId: Long): ApplicationReview =
            ApplicationReview(reviewerAdminId, ReviewDecision.APPROVED, null)

        fun rejected(reviewerAdminId: Long, rejectionReason: String): ApplicationReview {
            if (rejectionReason.isBlank()) {
                throw BusinessException(SellerErrorCode.REJECTION_REASON_REQUIRED)
            }
            return ApplicationReview(reviewerAdminId, ReviewDecision.REJECTED, rejectionReason)
        }
    }
}
