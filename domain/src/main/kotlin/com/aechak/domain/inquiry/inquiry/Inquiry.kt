package com.aechak.domain.inquiry.inquiry

import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.aechak.domain.inquiry.inquiry.event.InquiryReceivedEvent
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 앱 서비스 문의 */
@Entity
@Table(name = "admin_inquiries")
class Inquiry protected constructor(
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val inquiryType: InquiryType,
    @Column(nullable = false, length = 255)
    val replyEmail: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InquiryStatus = InquiryStatus.RECEIVED
        protected set

    /** 접수 사실 이벤트 등록 */
    fun registerReceived() =
        registerEvent(
            InquiryReceivedEvent(
                inquiryId = id,
                inquiryType = inquiryType,
                userId = userId,
                replyEmail = replyEmail,
                content = content,
                createdAt = createdAt,
            ),
        )

    companion object {
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_REPLY_EMAIL_LENGTH = 255

        private val REPLY_EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun receive(
            userId: Long,
            inquiryType: InquiryType,
            replyEmail: String,
            content: String,
        ): Inquiry {
            if (content.isBlank()) {
                throw BusinessException(InquiryErrorCode.INQUIRY_CONTENT_REQUIRED)
            }
            if (content.length > MAX_CONTENT_LENGTH) {
                throw BusinessException(InquiryErrorCode.INQUIRY_CONTENT_TOO_LONG)
            }
            if (replyEmail.isBlank()) {
                throw BusinessException(InquiryErrorCode.INQUIRY_REPLY_EMAIL_REQUIRED)
            }
            if (replyEmail.length > MAX_REPLY_EMAIL_LENGTH) {
                throw BusinessException(InquiryErrorCode.INQUIRY_REPLY_EMAIL_TOO_LONG)
            }
            if (!REPLY_EMAIL_REGEX.matches(replyEmail)) {
                throw BusinessException(InquiryErrorCode.INQUIRY_INVALID_REPLY_EMAIL)
            }
            return Inquiry(userId, inquiryType, replyEmail, content)
        }
    }
}
