package com.aechak.api.file.response

import com.aechak.application.file.usecase.result.IssuePresignedUrlResult

data class PresignedUrlResponse(
    val url: String,
    val key: String,
) {
    companion object {
        fun from(result: IssuePresignedUrlResult): PresignedUrlResponse = PresignedUrlResponse(url = result.url, key = result.key)
    }
}
