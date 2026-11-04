package com.aechak.application.sns.usecase.command

data class CreatePostCommand(
    val userId: Long,
    val idempotencyKey: String,
    val caption: String?,
    val photoKeys: List<String>,
    val hashtags: List<String> = emptyList(),
)
