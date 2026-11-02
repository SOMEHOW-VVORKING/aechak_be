package com.aechak.domain.notice.notice

import com.aechak.common.error.BusinessException
import com.aechak.domain.notice.error.NoticeErrorCode
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.domain.notice.notice.enums.NoticeStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 운영자가 쓰고 구매자·셀러가 읽는 서비스 공지.
 *
 * 작성은 초안(DRAFT)으로 시작하고, 게시(PUBLISHED)된 것만 읽는 쪽에 보인다.
 * 내용 수정은 상태와 무관하게 허용한다 — 오타·날짜 정정은 게시 중에도 필요하고,
 * 고치려면 내렸다 올려야 한다는 규칙은 그 사이에 공지가 사라지는 부작용을 만든다.
 */
@Entity
@Table(
    name = "notices",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_notices_public_id", columnNames = ["public_id"]),
    ],
    indexes = [
        // 읽는 쪽 목록: status·audience로 걸러 (pinned desc, published_at desc, id desc)로 읽는다.
        // 정렬 세 키를 꼬리에 둬 커버링 + 동시각 타이브레이크.
        Index(
            name = "idx_notices_status_audience_pinned_published_at",
            columnList = "status, audience, pinned, published_at, id",
        ),
        // 어드민 목록: 상태 필터 + 최근 작성순. 상태 필터가 없는 전체 목록도 이 인덱스를 쓴다.
        Index(name = "idx_notices_status_created_at", columnList = "status, created_at, id"),
        Index(name = "idx_notices_created_at_id", columnList = "created_at, id"),
    ],
)
class Notice protected constructor(
    title: String,
    content: String,
    category: NoticeCategory,
    audience: NoticeAudience,
    pinned: Boolean,
    internalMemo: String?,
    createdByAdminId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /** 외부 노출 식별자. 공지 경로는 전부 이 값으로 받고 내부 id는 응답에 싣지 않는다. */
    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    var title: String = title
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: NoticeCategory = category
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var audience: NoticeAudience = audience
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: NoticeStatus = NoticeStatus.DRAFT
        protected set

    /** 읽는 쪽 목록 상단 고정. 셀러 화면은 이 값을 '중요' 표기로 쓴다. */
    @Column(nullable = false)
    var pinned: Boolean = pinned
        protected set

    /**
     * 가장 최근 게시 시각. 읽는 쪽의 정렬 키이자 '보낸 시각' 표기다.
     * 내렸다 다시 게시하면 갱신한다 — 독자에게는 그때 새로 도착한 공지다.
     * 한 번도 게시되지 않았으면 null.
     */
    @Column
    var publishedAt: LocalDateTime? = null
        protected set

    /** 운영자 내부 메모. 읽는 쪽 응답에 절대 싣지 않는다. */
    @Column(length = MAX_MEMO_LENGTH)
    var internalMemo: String? = internalMemo
        protected set

    @Column(nullable = false, updatable = false)
    val createdByAdminId: Long = createdByAdminId

    /** 내용 수정·게시·내림을 마지막으로 수행한 운영자 — 운영 기록이자 책임 추적 근거. */
    @Column
    var lastModifiedByAdminId: Long? = null
        protected set

    /** 내용 수정. 상태는 건드리지 않는다 — 게시 중 공지를 고쳐도 게시 상태로 남는다. */
    fun edit(
        adminId: Long,
        title: String,
        content: String,
        category: NoticeCategory,
        audience: NoticeAudience,
        pinned: Boolean,
        internalMemo: String?,
    ) {
        validate(title, content, internalMemo)
        this.title = title
        this.content = content
        this.category = category
        this.audience = audience
        this.pinned = pinned
        this.internalMemo = internalMemo
        lastModifiedByAdminId = adminId
    }

    /**
     * 게시. 초안과 내린 공지만 올릴 수 있다 — 이미 게시 중인 공지를 다시 올리면
     * publishedAt이 갱신되어 독자 목록에서 맨 위로 튀어오른다(내용은 그대로인데).
     */
    fun publish(
        adminId: Long,
        publishedAt: LocalDateTime,
    ) {
        if (status == NoticeStatus.PUBLISHED) {
            throw BusinessException(NoticeErrorCode.NOTICE_ALREADY_PUBLISHED)
        }
        status = NoticeStatus.PUBLISHED
        this.publishedAt = publishedAt
        lastModifiedByAdminId = adminId
    }

    /** 내림. 게시 중인 공지만 내릴 수 있다. publishedAt은 지우지 않는다 — 게시한 사실의 기록이다. */
    fun unpublish(adminId: Long) {
        if (status != NoticeStatus.PUBLISHED) {
            throw BusinessException(NoticeErrorCode.NOTICE_NOT_PUBLISHED)
        }
        status = NoticeStatus.UNPUBLISHED
        lastModifiedByAdminId = adminId
    }

    /** 이 공지를 그 대상의 독자가 읽을 수 있는가 — 상세 조회의 인가 판정. */
    fun isVisibleTo(reader: NoticeAudience): Boolean = status == NoticeStatus.PUBLISHED && audience in reader.visibleTo()

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_MEMO_LENGTH = 300

        /** 초안 생성. 게시는 별도 전이다 — 쓰는 즉시 나가는 경로를 두지 않는다. */
        fun draft(
            adminId: Long,
            title: String,
            content: String,
            category: NoticeCategory,
            audience: NoticeAudience,
            pinned: Boolean,
            internalMemo: String?,
        ): Notice {
            validate(title, content, internalMemo)
            return Notice(title, content, category, audience, pinned, internalMemo, adminId)
        }

        private fun validate(
            title: String,
            content: String,
            internalMemo: String?,
        ) {
            if (title.isBlank()) {
                throw BusinessException(NoticeErrorCode.NOTICE_TITLE_REQUIRED)
            }
            if (title.length > MAX_TITLE_LENGTH) {
                throw BusinessException(NoticeErrorCode.NOTICE_TITLE_TOO_LONG)
            }
            if (content.isBlank()) {
                throw BusinessException(NoticeErrorCode.NOTICE_CONTENT_REQUIRED)
            }
            if (content.length > MAX_CONTENT_LENGTH) {
                throw BusinessException(NoticeErrorCode.NOTICE_CONTENT_TOO_LONG)
            }
            if (internalMemo != null && internalMemo.length > MAX_MEMO_LENGTH) {
                throw BusinessException(NoticeErrorCode.NOTICE_MEMO_TOO_LONG)
            }
        }
    }
}
