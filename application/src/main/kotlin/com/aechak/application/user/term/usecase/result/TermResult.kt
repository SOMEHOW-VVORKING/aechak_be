package com.aechak.application.user.term.usecase.result

import com.aechak.domain.user.term.Term
import com.aechak.domain.user.term.enums.TermType
import java.time.LocalDateTime

data class TermResult(
    val termId: Long,
    val type: TermType,
    val isRequired: Boolean,
    val version: String,
    val title: String,
    val body: String,
    val effectiveAt: LocalDateTime,
) {
    companion object {
        fun from(term: Term): TermResult =
            TermResult(
                termId = term.id,
                type = term.type,
                isRequired = term.isRequired,
                version = term.version,
                title = term.title,
                body = term.body,
                effectiveAt = term.effectiveAt,
            )
    }
}
