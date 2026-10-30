package com.aechak.infra.persistence.report

import com.aechak.application.report.admin.port.ReportReviewVisibilityPort
import com.aechak.domain.review.review.Review
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품.
 * 상속받은 `findById` 에는 `@Lock` 을 걸 수 없어 잠금 전용 메서드를 따로 둔다.
 */
interface ReportReviewLockJpaRepository : JpaRepository<Review, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Review?
}

/**
 * 리뷰 행을 잠그고 노출 여부만 꺼낸다. 뒤따르는 비노출 위임
 * (`AdminReviewService.blind` → `findByIdForUpdate`)이 같은 행을 같은 잠금으로 다시 읽으므로
 * 판정과 조치가 같은 상태를 본다.
 *
 * 전제: 호출부가 이 트랜잭션에서 그 리뷰를 **처음 적재하는 자리**여야 한다. 이미 영속성 컨텍스트에
 * 올라와 있으면 잠금 조회가 관리 중인 인스턴스를 그대로 돌려주고 방금 읽은 행 값을 버려, 잠금만
 * 잡고 낡은 상태를 판정하게 된다. 지금 호출부(신고 조회)는 스칼라 투영이라 엔티티를 올리지 않는다.
 */
@Repository
class ReportReviewVisibilityAdapter(
    private val jpaRepository: ReportReviewLockJpaRepository,
) : ReportReviewVisibilityPort {
    /** 노출 중(PUBLIC·MASKED)이 아니면 가려진 것으로 본다 — 삭제된 리뷰도 가려진 쪽이다. */
    override fun lockAndReadHidden(reviewId: Long): Boolean? =
        jpaRepository.findByIdForUpdate(reviewId)?.let { !it.reviewStatus.isVisible() }
}
