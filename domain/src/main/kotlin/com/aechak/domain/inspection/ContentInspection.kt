package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Duration
import java.time.LocalDateTime

/**
 * 콘텐츠 검수 한 건.
 *
 * 같은 대상의 같은 내용은 같은 정책 아래에서 한 번만 판정한다. 그 유일성이 재과금을 막는다.
 * 정책 문구를 고치면 `policyVersion`이 올라가 옛 판정을 재사용하지 않는다.
 */
@Entity
@Table(
    name = "content_inspections",
    // 플라이웨이에도 같은 제약이 있지만 엔티티에도 선언한다 — ddl-auto로 스키마를 만드는
    // 테스트 컨텍스트에서는 엔티티 메타데이터만 보고 테이블을 만들기 때문에,
    // 선언하지 않으면 그 환경에서만 중복이 통과해 재과금 방지가 테스트되지 않는다.
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_content_inspections_target_hash",
            columnNames = ["target_type", "target_ref", "content_hash", "policy_version"],
        ),
    ],
)
class ContentInspection protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val targetType: InspectionTargetType,
    @Column(nullable = false, updatable = false)
    val targetRef: Long,
    // 길이뿐 아니라 타입까지 못박는다. 플라이웨이가 CHAR(64)로 만드는데 여기서 비워 두면
    // ddl-auto 환경만 VARCHAR(64)가 되어 환경 간 스키마가 갈라진다.
    @Column(nullable = false, columnDefinition = "CHAR(64)", updatable = false)
    val contentHash: String,
    @Column(nullable = false, length = 40, updatable = false)
    val policyVersion: String,
    @Column(updatable = false)
    val targetVersionNo: Int?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var processingState: InspectionProcessingState = InspectionProcessingState.QUEUED
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var judgment: InspectionJudgment? = null
        protected set

    @Column(length = 40)
    var promptVersion: String? = null
        protected set

    @Column(length = 60)
    var reasonCode: String? = null
        protected set

    @Column(length = 500)
    var reasonSummary: String? = null
        protected set

    @Column(columnDefinition = "TEXT")
    var sellerNotice: String? = null
        protected set

    @Column(nullable = false)
    var attempt: Int = 0
        protected set

    @Column(length = 64)
    var leaseOwner: String? = null
        protected set

    var leaseExpiresAt: LocalDateTime? = null
        protected set

    var nextRetryAt: LocalDateTime? = null
        protected set

    /** 최초 요청 시각. 재시도로 갱신하지 않는다 — 밀면 기한이 영원히 오지 않는다 */
    @Column(nullable = false, updatable = false)
    var requestedAt: LocalDateTime = LocalDateTime.now()
        protected set

    var settledAt: LocalDateTime? = null
        protected set

    @Version
    @Column(nullable = false)
    var version: Int = 0
        protected set

    /** 아직 판정을 받지 못했는가. 타임아웃 승격의 유일한 조건이다 */
    fun isUnjudged(): Boolean = judgment == null

    /**
     * 기한이 지나 더 기다리지 않을 건가.
     *
     * 명시적으로 보류 판정이 난 건은 대상이 아니다. 그것까지 풀어 주면 검수가 장식이 된다.
     */
    fun isExpired(
        now: LocalDateTime,
        waitLimit: Duration,
    ): Boolean =
        processingState != InspectionProcessingState.SETTLED &&
            isUnjudged() &&
            requestedAt.plus(waitLimit).isBefore(now)

    /** 재발행 대상인가. 임차가 살아 있으면 다른 인스턴스가 들고 있는 것이다 */
    fun isRetryDue(now: LocalDateTime): Boolean {
        if (processingState == InspectionProcessingState.SETTLED) return false
        if (leaseExpiresAt?.isAfter(now) == true) return false
        return nextRetryAt?.isAfter(now) != true
    }

    /** 발행을 선점한다. 같은 건을 여러 인스턴스가 동시에 보내는 것을 막는다 */
    fun lease(
        owner: String,
        now: LocalDateTime,
        leaseFor: Duration,
        retryAfter: Duration,
    ) {
        processingState = InspectionProcessingState.IN_FLIGHT
        leaseOwner = owner
        leaseExpiresAt = now.plus(leaseFor)
        nextRetryAt = now.plus(retryAfter)
        attempt += 1
    }

    /**
     * 판정을 적는다.
     *
     * 늦게 도착한 중복 판정을 막는 것은 호출부의 조건부 갱신이고, 여기서는 상태만 옮긴다.
     */
    fun settle(
        judgment: InspectionJudgment,
        promptVersion: String?,
        reasonCode: String?,
        reasonSummary: String?,
        sellerNotice: String?,
        now: LocalDateTime,
    ) {
        this.judgment = judgment
        this.promptVersion = promptVersion
        this.reasonCode = reasonCode
        this.reasonSummary = reasonSummary
        this.sellerNotice = sellerNotice
        processingState = InspectionProcessingState.SETTLED
        settledAt = now
        leaseOwner = null
        leaseExpiresAt = null
        nextRetryAt = null
    }

    /** 기한이 지나 판정 없이 닫는다. 통과가 아니라 미판정으로 남긴다 */
    fun expire(now: LocalDateTime) {
        judgment = InspectionJudgment.UNJUDGED
        processingState = InspectionProcessingState.SETTLED
        settledAt = now
        leaseOwner = null
        leaseExpiresAt = null
        nextRetryAt = null
    }

    companion object {
        fun queue(
            targetType: InspectionTargetType,
            targetRef: Long,
            contentHash: String,
            policyVersion: String,
            targetVersionNo: Int? = null,
        ): ContentInspection = ContentInspection(targetType, targetRef, contentHash, policyVersion, targetVersionNo)
    }
}
