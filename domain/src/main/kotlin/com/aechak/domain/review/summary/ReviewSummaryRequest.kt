package com.aechak.domain.review.summary

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Duration
import java.time.LocalDateTime

/** 상품별 마지막 요약 요청. 한 상품에 응답을 기다리는 요청은 하나뿐임 */
@Entity
@Table(name = "review_summary_requests")
class ReviewSummaryRequest protected constructor(
    @Id
    val productId: Long,
) : BaseEntity() {
    @Column(length = 40)
    var requestId: String? = null
        protected set

    var requestedAt: LocalDateTime? = null
        protected set

    /** 이 시각 전에는 다시 요청하지 않음. 응답을 기다리는 중이거나 실패 뒤 쉬는 중 */
    var retryAfter: LocalDateTime? = null
        protected set

    /** 모든 범위의 요약이 최신이라고 확인했을 때의 상품 노출 리뷰 지문 */
    @Column(length = 60)
    var syncedSignature: String? = null
        protected set

    @Column(length = 255)
    var expectedSignatures: String? = null
        protected set

    // 배치의 요청 시작과 컨슈머의 응답 반영이 같은 행을 고침. 늦게 커밋한 쪽이 앞의 요청 번호를 되돌리지 않게 막음
    @Version
    var version: Long = 0L
        protected set

    fun isLatest(requestId: String): Boolean = this.requestId == requestId

    fun start(
        requestId: String,
        expectedSignatureByScopeKey: Map<String, String>,
        now: LocalDateTime,
        responseTimeout: Duration,
    ) {
        this.requestId = requestId
        requestedAt = now
        retryAfter = now.plus(responseTimeout)
        // 확인 지문을 남겨 두면 리뷰가 늘었다가 원래대로 돌아왔을 때 지금 지문과 같아져, 그 사이 만든 요약을 다시 보지 않음
        syncedSignature = null
        expectedSignatures = expectedSignatureByScopeKey.entries.joinToString(ENTRY_SEPARATOR) { "${it.key}$KEY_SEPARATOR${it.value}" }
    }

    /** 요청을 보낼 때 기대한 범위별 지문. 요청에 없던 범위면 null */
    fun findExpectedSignature(scopeKey: String): String? =
        expectedSignatures
            ?.split(ENTRY_SEPARATOR)
            ?.map { it.split(KEY_SEPARATOR, limit = 2) }
            ?.firstOrNull { it.first() == scopeKey }
            ?.last()

    fun complete() {
        retryAfter = null
    }

    fun backOff(
        now: LocalDateTime,
        delay: Duration,
    ) {
        retryAfter = now.plus(delay)
    }

    fun markSynced(signature: String) {
        syncedSignature = signature
    }

    companion object {
        private const val ENTRY_SEPARATOR = ";"
        private const val KEY_SEPARATOR = "="

        fun create(productId: Long): ReviewSummaryRequest = ReviewSummaryRequest(productId)
    }
}
