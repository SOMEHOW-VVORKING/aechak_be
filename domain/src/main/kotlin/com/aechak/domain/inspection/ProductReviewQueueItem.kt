package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueBlockSource
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * AI가 문제를 찾았거나 판단하지 못해 관리자가 확인해야 하는 상품 한 건.
 *
 * 상품마다 열린 행(PENDING_REVIEW)은 하나뿐임. DB 제약이 아니라 상품 행 잠금 아래에서 열린 행을 찾고 쓰는 것으로 지킴.
 * 닫힌 행은 지우지 않고 이력으로 남김.
 *
 * 셀러 이의도 이 행에 적힘. 사람이 내리는 결정의 축이 이미 여기로 모여 있고([status], [rejectReason]),
 * 이의는 그 결정의 입력이기 때문임. 검수 행에 두면 결정 한 번이 두 테이블·두 잠금을 건드리고
 * 잠금 순서가 하나 더 생김.
 *
 * 이의 상태를 열거로 따로 두지 않음 — [appealedAt]이 비었는가와 [status]가 어떻게 닫혔는가에서 파생됨.
 * 열린 행은 언제나 닫힐 수 있어서 "관리자 상태"와 "이의 상태"가 갈라질 자리가 없음.
 */
@Entity
@Table(
    name = "product_review_queue_items",
    // 마이그레이션의 인덱스를 같은 이름·같은 칼럼 순서로 모두 선언함. 한쪽만 두면 ddl-auto로
    // 스키마를 만드는 테스트·로컬만 인덱스 없이 돌아 조회 계획이 환경 간에 갈린다 —
    // 어드민 목록(status 등호 + blinded_at 정렬)이 한쪽에서는 파일소트가 된다
    indexes = [
        Index(name = "ix_product_review_queue_items_target", columnList = "target_type, target_id, status"),
        Index(name = "ix_product_review_queue_items_status", columnList = "status, blinded_at"),
        Index(name = "ix_product_review_queue_items_owner", columnList = "owner_id, status"),
    ],
)
class ProductReviewQueueItem protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val targetType: InspectionTargetType,
    @Column(nullable = false, updatable = false)
    val targetId: Long,
    @Column(nullable = false, updatable = false)
    val ownerId: Long,
    targetName: String?,
    blindedAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Column(length = TARGET_NAME_MAX)
    var targetName: String? = targetName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    val blockSource: ReviewQueueBlockSource = ReviewQueueBlockSource.AI_DETECTION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ReviewQueueStatus = ReviewQueueStatus.PENDING_REVIEW
        protected set

    /** 이 행을 열었거나 다시 연 판정의 시각 */
    @Column(nullable = false)
    var blindedAt: LocalDateTime = blindedAt
        protected set

    @Column(length = REJECT_REASON_MAX)
    var rejectReason: String? = null
        protected set

    /** 셀러가 쓴 문장. 관리자에게 그대로 보임 */
    @Column(length = APPEAL_REASON_MAX)
    var appealReason: String? = null
        protected set

    /** null이면 이의가 걸리지 않은 행. 관리자 목록의 '이의' 필터가 읽는 축임 */
    var appealedAt: LocalDateTime? = null
        protected set

    /**
     * 이의를 접수한 시점의 내용 지문. 그 이의가 **무엇에 대한 것인지**임.
     *
     * 새 판정이 왔을 때 이의를 비울지 가르는 축이다([refresh]). 지문을 모르는 접수도 있어서
     * "이의가 있었는가"의 축으로는 쓰지 않음 — 그쪽은 [appealAttemptCount]다. 한 칼럼이 둘을
     * 겸하면 지문이 없는 이의에서 무효 흔적이 함께 사라진다.
     */
    @Column(length = CONTENT_HASH_MAX)
    var appealedContentHash: String? = null
        protected set

    /**
     * 이 행에 이의가 접수된 **횟수**. 무효화가 지우지 않는 유일한 이의 축임.
     *
     * 두 가지를 든다. 첫째, 무효화된 이의의 흔적이다 — [appealedAt]이 비었는데 이 숫자가 0이
     * 아니면 "접수했지만 무효가 됐다"이고, 그러지 않으면 셀러 응답이 그것을 "접수한 적 없음"과
     * 같은 모양으로 내려보낸다. 둘째, 폭주 상한의 분모다. 무효화는 재접수를 열어 주는 장치라
     * 라이브 칼럼([appealedAt])을 비우는데, 그 칼럼이 상한의 분모까지 겸하면 "한 글자 고쳐
     * 새 판정을 받고 다시 접수"가 아무 상한에도 걸리지 않고 무한히 돈다.
     */
    @Column(nullable = false)
    var appealAttemptCount: Int = 0
        protected set

    /**
     * 행이 닫힌 시각. 기각 쿨다운의 기준임.
     *
     * [BaseEntity]의 수정 시각으로 대신하지 않음 — "닫힌 행에는 더 쓰지 않는다"는 지금의 사실에만
     * 기대는 것이고, 나중에 닫힌 행에 쓰는 경로가 하나 생기는 순간 쿨다운이 조용히 밀림.
     */
    var resolvedAt: LocalDateTime? = null
        protected set

    fun isPending(): Boolean = status == ReviewQueueStatus.PENDING_REVIEW

    /** 이의가 접수됐다가 내용이 바뀌어 무효가 된 행인가 */
    fun appealCleared(): Boolean = appealedAt == null && appealAttemptCount > 0

    /**
     * 열린 행에 새 판정이 또 왔을 때. 행을 새로 만들지 않고 시각과 상품명만 갱신함.
     *
     * 이의는 **내용이 바뀌었을 때만** 비움. 바뀌면 셀러가 적은 사유가 지금 걸린 것을 가리키지
     * 않아서, 그대로 두면 관리자는 "셀러가 A에 억울하다 했는데 지금 걸린 것은 B"를 보고 셀러는
     * 재접수도 막혀 할 수 있는 것이 없어짐. 비우면 재접수가 열리고 셀러 상한의 정원도 비워짐.
     *
     * 반대로 내용이 그대로인 판정으로 비우면 안 됨. 관리자가 반려한 상품은 어떤 판정이 와도
     * 관리자가 다시 봐야 해서 매번 이 메서드로 들어오는데, 셀러가 한 일이 없는데 거기서 지우면
     * 모델 장애가 곧 "제출한 이의를 아무도 못 보는 상태"가 됨. 지문을 모르는
     * 이의([appealedContentHash]가 비었음)는 가릴 근거가 없어 비움.
     *
     * 비울 때 [appealAttemptCount]는 건드리지 않음. 그것이 "이의가 있었고 무효가 됐다"는
     * 흔적이고 동시에 되돌이를 끊는 분모임.
     */
    fun refresh(
        targetName: String?,
        blindedAt: LocalDateTime,
        contentHash: String?,
    ) {
        requirePending()
        this.targetName = targetName
        this.blindedAt = blindedAt
        if (appealedAt == null || appealedContentHash == contentHash) return

        appealReason = null
        appealedAt = null
    }

    /**
     * 셀러 이의를 접수함. 행이 닫히는 방식이 곧 이의의 답임 — 수용은 [approve], 기각은 [reject]다.
     *
     * [blindedAt]은 건드리지 않음. 관리자 목록의 정렬 키라서, 접수가 밀면 이의 폭주가 곧
     * 큐 정렬 조작이 됨.
     */
    fun appeal(
        reason: String,
        now: LocalDateTime,
        contentHash: String?,
    ) {
        requirePending()
        require(reason.isNotBlank()) { "이의 사유가 비어 있음" }
        check(appealedAt == null) { "이미 이의가 걸린 행임. id=$id" }
        appealReason = reason
        appealedAt = now
        appealedContentHash = contentHash
        appealAttemptCount += 1
    }

    fun approve(now: LocalDateTime) {
        requirePending()
        close(ReviewQueueStatus.APPROVED, now)
    }

    fun reject(
        reason: String,
        now: LocalDateTime,
    ) {
        requirePending()
        require(reason.isNotBlank()) { "반려 사유가 비어 있음" }
        rejectReason = reason
        close(ReviewQueueStatus.REJECTED, now)
    }

    fun supersede(now: LocalDateTime) {
        requirePending()
        close(ReviewQueueStatus.SUPERSEDED, now)
    }

    private fun close(
        status: ReviewQueueStatus,
        now: LocalDateTime,
    ) {
        this.status = status
        resolvedAt = now
    }

    private fun requirePending() {
        check(isPending()) { "이미 닫힌 검토 행임. id=$id status=$status" }
    }

    companion object {
        const val TARGET_NAME_MAX = 255
        const val CONTENT_HASH_MAX = 64
        const val REJECT_REASON_MAX = 500
        const val APPEAL_REASON_MAX = 500

        fun openForProduct(
            productId: Long,
            sellerId: Long,
            productName: String?,
            blindedAt: LocalDateTime,
        ): ProductReviewQueueItem =
            ProductReviewQueueItem(
                targetType = InspectionTargetType.PRODUCT,
                targetId = productId,
                ownerId = sellerId,
                targetName = productName,
                blindedAt = blindedAt,
            )
    }
}
