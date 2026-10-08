package com.aechak.infra.persistence.user.report

import com.aechak.domain.user.report.UserReport
import com.aechak.domain.user.report.repository.DuplicateUserReportException
import com.aechak.domain.user.report.repository.UserReportRepository
import com.aechak.infra.persistence.support.isUniqueViolationOf
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface UserReportJpaRepository : JpaRepository<UserReport, Long>

@Repository
class UserReportRepositoryAdapter(
    private val jpaRepository: UserReportJpaRepository,
) : UserReportRepository {
    override fun save(report: UserReport): UserReport =
        try {
            jpaRepository.saveAndFlush(report)
        } catch (e: DataIntegrityViolationException) {
            if (e.isUniqueViolationOf(UserReport.UK_TARGET_USER_ID_REPORTER_ID)) {
                throw DuplicateUserReportException(e)
            }
            throw e
        }
}
