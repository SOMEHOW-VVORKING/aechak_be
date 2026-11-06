package com.aechak.infra.persistence.sns.report

import com.aechak.domain.sns.report.PostReport
import com.aechak.domain.sns.report.repository.DuplicatePostReportException
import com.aechak.domain.sns.report.repository.PostReportRepository
import com.aechak.infra.persistence.support.isUniqueViolationOf
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface PostReportJpaRepository : JpaRepository<PostReport, Long>

@Repository
class PostReportRepositoryAdapter(
    private val jpaRepository: PostReportJpaRepository,
) : PostReportRepository {
    override fun save(report: PostReport): PostReport =
        try {
            jpaRepository.saveAndFlush(report)
        } catch (e: DataIntegrityViolationException) {
            if (e.isUniqueViolationOf(PostReport.UK_POST_ID_REPORTER_ID)) throw DuplicatePostReportException(e)
            throw e
        }
}
