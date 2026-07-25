package com.aechak.api.user.term.response

import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.domain.user.term.enums.TermType
import java.time.LocalDateTime

data class TermResponse(
    val termId: Long,
    val type: TermType,
    val isRequired: Boolean,
    val version: String,
    val title: String,
    val body: String,
    val effectiveAt: LocalDateTime,
) {
    companion object {
        fun from(result: TermResult): TermResponse =
            TermResponse(
                termId = result.termId,
                type = result.type,
                isRequired = result.isRequired,
                version = result.version,
                title = result.title,
                body = result.body,
                effectiveAt = result.effectiveAt,
            )
    }
}
