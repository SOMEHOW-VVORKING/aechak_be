package com.aechak.infra.persistence.sns.report

import com.aechak.domain.sns.report.CommentReport
import com.aechak.domain.sns.report.repository.CommentReportRepository
import com.aechak.domain.sns.report.repository.DuplicateCommentReportException
import com.aechak.infra.persistence.support.isUniqueViolationOf
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface CommentReportJpaRepository : JpaRepository<CommentReport, Long>

@Repository
class CommentReportRepositoryAdapter(
    private val jpaRepository: CommentReportJpaRepository,
) : CommentReportRepository {
    override fun save(report: CommentReport): CommentReport =
        try {
            jpaRepository.saveAndFlush(report)
        } catch (e: DataIntegrityViolationException) {
            if (e.isUniqueViolationOf(CommentReport.UK_COMMENT_ID_REPORTER_ID)) throw DuplicateCommentReportException(e)
            throw e
        }
}
