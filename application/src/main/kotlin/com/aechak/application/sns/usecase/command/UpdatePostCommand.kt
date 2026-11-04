package com.aechak.application.sns.usecase.command

data class UpdatePostCommand(
    val userId: Long,
    val publicId: String,
    val caption: String?,
    val hashtags: List<String>,
)
