package com.aechak.domain.inquiry.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 상품 문의 애그리거트 루트. 구매자가 상품에 묻고 셀러가 답한다.
 *
 * 상품·사용자는 다른 BC라 값참조(Long)로만 잡고 FK를 걸지 않는다. 상품명과 셀러는
 * 접수 시점 값을 복사해 두므로 상품이 판매 중지·삭제돼도 문의와 셀러의 받은 목록은 남는다.
 * 답변 기한도 접수 시점에 계산해 저장한다 — 프런트가 날짜를 빼서 "기한 지남"을 따로 셈하면
 * 서버 집계와 화면 숫자가 갈린다.
 */
@Entity
@Table(
    name = "product_inquiries",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_inquiries_public_id", columnNames = ["public_id"])],
    indexes = [
        Index(name = "ix_product_inquiries_product_status_id", columnList = "product_id, status, id"),
        Index(name = "ix_product_inquiries_seller_status_id", columnList = "seller_id, status, id"),
        Index(name = "ix_product_inquiries_author_id", columnList = "author_user_id, id"),
    ],
)
class ProductInquiry protected constructor(
    val productId: Long,
    @Column(nullable = false, updatable = false, length = 26)
    val productPublicId: String,
    val sellerId: Long,
    val authorUserId: Long,
    @Column(nullable = false, length = PRODUCT_NAME_SNAPSHOT_MAX)
    val productNameSnapshot: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: ProductInquiryCategory,
    @Column(nullable = false, length = TITLE_MAX)
    val title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(nullable = false)
    val secret: Boolean,
    @Column(nullable = false)
    val answerDueAt: LocalDateTime,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ProductInquiryStatus = ProductInquiryStatus.RECEIVED
        protected set

    var answeredAt: LocalDateTime? = null
        protected set

    var withdrawnAt: LocalDateTime? = null
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "inquiry_id", nullable = false, updatable = false)
    private val _answers: MutableList<ProductInquiryAnswer> = mutableListOf()
    val answers: List<ProductInquiryAnswer> get() = _answers.toList()

    /** 답변은 최대 한 건 — 상태 가드가 두 번째 답변을 막고 DB UNIQUE(inquiry_id)가 함께 받친다. */
    val answer: ProductInquiryAnswer? get() = answers.firstOrNull()

    /** 셀러 답변 등록. 철회된 문의와 이미 답한 문의는 받지 않는다. */
    fun writeAnswer(
        answererUserId: Long,
        content: String,
        now: LocalDateTime,
    ) {
        when (status) {
            ProductInquiryStatus.WITHDRAWN -> throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_WITHDRAWN)
            ProductInquiryStatus.ANSWERED -> throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ALREADY_ANSWERED)
            ProductInquiryStatus.RECEIVED -> Unit
        }
        _answers += ProductInquiryAnswer.of(answererUserId, validatedAnswerContent(content))
        status = ProductInquiryStatus.ANSWERED
        answeredAt = now
    }

    /** 구매자 철회. 답변이 달린 뒤에는 셀러가 쓴 글이 사라지므로 막는다. */
    fun withdraw(now: LocalDateTime) {
        when (status) {
            ProductInquiryStatus.WITHDRAWN -> throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ALREADY_WITHDRAWN)
            ProductInquiryStatus.ANSWERED -> throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_CANNOT_WITHDRAW_ANSWERED)
            ProductInquiryStatus.RECEIVED -> Unit
        }
        status = ProductInquiryStatus.WITHDRAWN
        withdrawnAt = now
    }

    /** 미답변 상태로 기한을 넘겼는지 — 답변·철회된 건은 기한 대상이 아니다. */
    fun isOverdue(now: LocalDateTime): Boolean = status == ProductInquiryStatus.RECEIVED && answerDueAt.isBefore(now)

    companion object {
        const val TITLE_MAX = 100
        const val CONTENT_MAX = 1000
        const val ANSWER_CONTENT_MAX = 2000
        const val PRODUCT_NAME_SNAPSHOT_MAX = 255

        /** 답변 기한 — 접수한 날의 다음 영업일부터 2영업일. */
        const val ANSWER_DUE_BUSINESS_DAYS = 2

        /** 남의 비밀글을 읽는 자리에 대신 넣는 본문. */
        const val SECRET_PLACEHOLDER = "비밀글입니다."

        /**
         * 접수 시점에서 영업일 2일째 되는 날의 끝.
         * 주말만 건너뛴다 — 공휴일 달력이 아직 없어서 공휴일은 영업일로 센다.
         * 나노초는 잘라낸다: DATETIME(6)에 저장할 값이라 999999999ns가 반올림으로 다음 날로 넘어갈 수 있다.
         */
        fun answerDeadline(askedAt: LocalDateTime): LocalDateTime {
            var date = askedAt.toLocalDate()
            var remaining = ANSWER_DUE_BUSINESS_DAYS
            while (remaining > 0) {
                date = date.plusDays(1)
                if (isBusinessDay(date)) remaining--
            }
            return date.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS)
        }

        private fun isBusinessDay(date: LocalDate): Boolean = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

        /**
         * 문의 접수. 구매 이력을 요구하지 않는다 — 구매 전 문의가 이 기능의 본래 용도다.
         * 다만 자기 상품에는 묻지 못한다(묻고 스스로 답하는 흐름을 막는다).
         */
        fun ask(
            productId: Long,
            productPublicId: String,
            sellerId: Long,
            authorUserId: Long,
            productNameSnapshot: String,
            category: ProductInquiryCategory,
            title: String,
            content: String,
            secret: Boolean,
            now: LocalDateTime,
        ): ProductInquiry {
            if (authorUserId == sellerId) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_SELF_ASK_FORBIDDEN)
            }
            return ProductInquiry(
                productId = productId,
                productPublicId = productPublicId,
                sellerId = sellerId,
                authorUserId = authorUserId,
                productNameSnapshot = productNameSnapshot.take(PRODUCT_NAME_SNAPSHOT_MAX),
                category = category,
                title = validatedTitle(title),
                content = validatedContent(content),
                secret = secret,
                answerDueAt = answerDeadline(now),
            )
        }

        private fun validatedTitle(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_TITLE_REQUIRED)
            }
            if (trimmed.length > TITLE_MAX) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_TITLE_TOO_LONG)
            }
            return trimmed
        }

        private fun validatedContent(content: String): String {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_CONTENT_REQUIRED)
            }
            if (trimmed.length > CONTENT_MAX) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_CONTENT_TOO_LONG)
            }
            return trimmed
        }

        private fun validatedAnswerContent(content: String): String {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ANSWER_REQUIRED)
            }
            if (trimmed.length > ANSWER_CONTENT_MAX) {
                throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ANSWER_TOO_LONG)
            }
            return trimmed
        }
    }
}
