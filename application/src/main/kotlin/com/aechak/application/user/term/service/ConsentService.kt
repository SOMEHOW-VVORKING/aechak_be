package com.aechak.application.user.term.service

import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.term.ConsentRecord
import com.aechak.domain.user.term.Term
import com.aechak.domain.user.term.repository.ConsentRecordRepository
import com.aechak.domain.user.term.repository.TermRepository
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Service

/**
 * 약관·동의 비즈니스 로직 보관함.
 *
 * 동의 제출은 온보딩 전용 append-only 원장 — 기존 행은 불변이고,
 * 유효 동의는 (user, term) 최신 행(MAX(id)) 기준으로 판정한다.
 */
@Service
class ConsentService(
    private val termRepository: TermRepository,
    private val consentRecordRepository: ConsentRecordRepository,
) {
    fun getActiveTerms(): List<Term> = termRepository.findAllActiveOrderedById()

    /** 온보딩 전이 게이트 — 활성 필수 약관 각각의 최신 행이 전부 isAgreed=true인가. */
    fun hasAllRequiredConsents(userId: Long): Boolean {
        val requiredTermIds =
            termRepository
                .findAllActiveOrderedById()
                .filter { it.isRequired }
                .map { it.id }
        if (requiredTermIds.isEmpty()) return true

        val latestByTermId =
            consentRecordRepository
                .findAllByUserAndTerms(userId, requiredTermIds)
                .groupBy { it.term.id }
                .mapValues { (_, rows) -> rows.maxBy { it.id } }
        return requiredTermIds.all { latestByTermId[it]?.isAgreed == true }
    }

    fun submit(
        user: User,
        items: List<SubmitConsentsCommand.ConsentItem>,
    ) {
        // 온보딩 전용 — ACTIVE 재제출은 동의 관리 API(후속) 몫이다.
        if (user.status != UserStatus.PENDING_ONBOARDING) {
            throw BusinessException(UserErrorCode.ONBOARDING_ALREADY_COMPLETED)
        }

        val activeTermById = termRepository.findAllActiveOrderedById().associateBy { it.id }
        if (!activeTermById.keys.containsAll(items.map { it.termId })) {
            throw BusinessException(UserErrorCode.INVALID_TERM)
        }

        // 필수 약관은 전부 포함 + isAgreed=true여야 한다 (false 제출·누락 모두 거부).
        val agreedByTermId = items.associate { it.termId to it.isAgreed }
        val requiredAllAgreed =
            activeTermById.values
                .filter { it.isRequired }
                .all { agreedByTermId[it.id] == true }
        if (!requiredAllAgreed) {
            throw BusinessException(UserErrorCode.REQUIRED_CONSENT_MISSING)
        }

        consentRecordRepository.saveAll(
            items.map { ConsentRecord.record(user, activeTermById.getValue(it.termId), it.isAgreed) },
        )
    }
}
