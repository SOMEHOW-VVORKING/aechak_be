package com.aechak.domain.inquiry.inquiry

import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.aechak.domain.inquiry.inquiry.event.InquiryAnsweredEvent
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
import java.time.LocalDateTime

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

    /** 운영자에게 전달된 답변 본문. 답변은 1회만 등록되므로 DONE 이후 바뀌지 않는다. */
    @Column(columnDefinition = "TEXT")
    var answer: String? = null
        protected set

    /** 답변을 등록한 운영자 — 운영 기록이자 책임 추적 근거. */
    @Column
    var answeredByAdminId: Long? = null
        protected set

    /** 이 문의를 집은 운영자 — 상태 전이·답변 등록이 갱신한다. */
    @Column
    var assignedAdminId: Long? = null
        protected set

    /** 완료(DONE) 전이 시각 = 답변 등록 시각. */
    @Column
    var resolvedAt: LocalDateTime? = null
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

    /**
     * 답변 등록 + 완료 전이. 답변은 1회만 가능하다 — 이미 완료된 문의는 덮어쓰지 않는다.
     * 본문 형식을 상태보다 먼저 거절해, 상태와 무관하게 같은 요청이 같은 코드로 떨어지게 한다.
     */
    fun registerAnswer(
        adminId: Long,
        body: String,
        answeredAt: LocalDateTime,
    ) {
        if (body.isBlank()) {
            throw BusinessException(InquiryErrorCode.INQUIRY_ANSWER_REQUIRED)
        }
        if (body.length > MAX_ANSWER_LENGTH) {
            throw BusinessException(InquiryErrorCode.INQUIRY_ANSWER_TOO_LONG)
        }
        if (status == InquiryStatus.DONE) {
            throw BusinessException(InquiryErrorCode.INQUIRY_ALREADY_ANSWERED)
        }
        answer = body
        answeredByAdminId = adminId
        assignedAdminId = adminId
        resolvedAt = answeredAt
        status = InquiryStatus.DONE
        registerEvent(
            InquiryAnsweredEvent(
                inquiryId = id,
                inquiryType = inquiryType,
                userId = userId,
                replyEmail = replyEmail,
                answer = body,
                answeredByAdminId = adminId,
                answeredAt = answeredAt,
            ),
        )
    }

    /**
     * 처리중·보류 전이 — 답변 없이 상태만 옮기고 담당 운영자를 남긴다.
     * 완료(DONE)로 가는 길은 답변 등록뿐이고, 완료된 문의는 되돌리지 않는다.
     * 같은 상태로의 전이도 거절한다 — 담당자만 바꿔치기하는 경로를 열지 않는다.
     */
    fun changeStatus(
        adminId: Long,
        target: InquiryStatus,
    ) {
        if (target !in MANUAL_TRANSITION_TARGETS || status == InquiryStatus.DONE || status == target) {
            throw BusinessException(InquiryErrorCode.INQUIRY_STATUS_TRANSITION_NOT_ALLOWED)
        }
        status = target
        assignedAdminId = adminId
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_REPLY_EMAIL_LENGTH = 255
        const val MAX_ANSWER_LENGTH = 1000

        /** 답변 없이 옮길 수 있는 상태. RECEIVED는 접수 시점의 초기값이라 되돌아갈 자리가 아니다. */
        val MANUAL_TRANSITION_TARGETS = setOf(InquiryStatus.IN_PROGRESS, InquiryStatus.ON_HOLD)

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
