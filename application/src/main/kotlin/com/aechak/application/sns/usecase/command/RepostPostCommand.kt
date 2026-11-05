package com.aechak.application.sns.usecase.command

data class RepostPostCommand(
    val userId: Long,
    val publicId: String,
)
