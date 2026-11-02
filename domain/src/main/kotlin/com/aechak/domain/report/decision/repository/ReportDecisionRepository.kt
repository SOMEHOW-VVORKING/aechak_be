package com.aechak.domain.report.decision.repository

import com.aechak.domain.report.decision.ReportDecision
import com.aechak.domain.report.decision.enums.ReportTargetType

interface ReportDecisionRepository {
    /**
     * 저장하고 즉시 flush 한다. 같은 신고에 결정이 이미 있으면 DuplicateReportDecisionException 을 던진다.
     * 즉시 flush 하는 이유: 중복 판정을 DB 의 UNIQUE 에 맡기므로, 트랜잭션 끝에 밀려 터지면
     * 비노출·제재 위임이 이미 실행된 뒤에 깨진다.
     */
    fun save(decision: ReportDecision): ReportDecision

    fun findByTarget(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportDecision?
}
