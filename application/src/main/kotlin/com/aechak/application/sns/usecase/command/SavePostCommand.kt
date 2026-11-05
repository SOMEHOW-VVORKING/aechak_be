package com.aechak.application.sns.usecase.command

data class SavePostCommand(
    val userId: Long,
    val publicId: String,
)
