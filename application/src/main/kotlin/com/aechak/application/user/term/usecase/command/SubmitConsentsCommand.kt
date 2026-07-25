package com.aechak.application.user.term.usecase.command

data class SubmitConsentsCommand(
    val userId: Long,
    val items: List<ConsentItem>,
) {
    data class ConsentItem(
        val termId: Long,
        val isAgreed: Boolean,
    )
}
