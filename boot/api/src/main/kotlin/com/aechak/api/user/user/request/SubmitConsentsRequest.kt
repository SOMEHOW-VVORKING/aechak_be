package com.aechak.api.user.user.request

import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty

data class SubmitConsentsRequest(
    @field:Valid
    @field:NotEmpty(message = "동의 항목은 1개 이상이어야 합니다.")
    val items: List<Item>,
) {
    data class Item(
        val termId: Long,
        val isAgreed: Boolean,
    )

    /** 같은 약관의 상반된 동의가 한 제출에 섞이지 않도록 termId 중복을 형식 오류로 거른다. */
    @get:AssertTrue(message = "같은 약관을 중복 제출할 수 없습니다.")
    val isTermIdUnique: Boolean
        get() = items.map { it.termId }.distinct().size == items.size

    fun toCommand(userId: Long): SubmitConsentsCommand =
        SubmitConsentsCommand(
            userId = userId,
            items = items.map { SubmitConsentsCommand.ConsentItem(termId = it.termId, isAgreed = it.isAgreed) },
        )
}
