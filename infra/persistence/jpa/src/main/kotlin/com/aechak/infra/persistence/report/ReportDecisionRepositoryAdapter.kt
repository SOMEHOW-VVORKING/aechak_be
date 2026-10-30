package com.aechak.infra.persistence.report

import com.aechak.domain.report.decision.ReportDecision
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.report.decision.repository.DuplicateReportDecisionException
import com.aechak.domain.report.decision.repository.ReportDecisionRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface ReportDecisionJpaRepository : JpaRepository<ReportDecision, Long> {
    @Query("select d from ReportDecision d where d.targetType = :targetType and d.reportId = :reportId")
    fun findByTarget(
        @Param("targetType") targetType: ReportTargetType,
        @Param("reportId") reportId: Long,
    ): ReportDecision?
}

@Repository
class ReportDecisionRepositoryAdapter(
    private val jpaRepository: ReportDecisionJpaRepository,
) : ReportDecisionRepository {
    /**
     * 저장 직후 flush 한다 — 트랜잭션 끝까지 밀리면 비노출·제재 위임이 끝난 뒤에 UNIQUE 위반이 터진다.
     * 그러면 중복 처리 요청이 대상을 한 번 더 숨긴 상태로 500 이 나간다.
     */
    override fun save(decision: ReportDecision): ReportDecision =
        try {
            jpaRepository.saveAndFlush(decision)
        } catch (e: DataIntegrityViolationException) {
            // 중복 처리로 바꾸는 것은 이 표의 UNIQUE 위반일 때만이다. NOT NULL·길이 초과까지
            // 409 로 바꾸면 운영자에게 "이미 처리됨"이라고 거짓말하고 진짜 결함이 묻힌다
            if (!isTargetUniqueViolation(e)) throw e
            throw DuplicateReportDecisionException(e)
        }

    override fun findByTarget(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportDecision? = jpaRepository.findByTarget(targetType, reportId)

    /**
     * 제약 이름으로 가린다. Hibernate 가 이름을 싣지 못한 경우를 위해 메시지도 함께 본다 —
     * 드라이버·방언에 따라 constraintName 이 비어 오는 경우가 있다.
     */
    private fun isTargetUniqueViolation(e: DataIntegrityViolationException): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is ConstraintViolationException &&
                cause.constraintName?.contains(ReportDecision.UK_TARGET, ignoreCase = true) == true
            ) {
                return true
            }
            cause = cause.cause
        }
        return e.message?.contains(ReportDecision.UK_TARGET, ignoreCase = true) == true
    }
}
