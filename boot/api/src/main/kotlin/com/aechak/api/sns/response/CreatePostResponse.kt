package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.CreatePostResult

data class CreatePostResponse(
    val publicId: String,
) {
    companion object {
        fun from(result: CreatePostResult): CreatePostResponse = CreatePostResponse(publicId = result.publicId)
    }
}
