package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsPostGridItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.sns.post.enums.PostMediaType
import io.swagger.v3.oas.annotations.media.Schema

/** 프로필 탭 한 페이지. 목록 응답과 달리 totalCount가 없다 */
data class SnsPostGridPageResponse(
    val items: List<SnsPostGridItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsPostGridItemResponse(
        @field:Schema(description = "게시물 공개 ID")
        val publicId: String,
        @field:Schema(description = "VIDEO면 칸에 재생 표시를 그린다")
        val mediaType: PostMediaType,
        @field:Schema(description = "사진 게시물은 첫 사진, 영상 게시물은 썸네일의 표시용 URL. 텍스트 게시물은 null")
        val thumbnailUrl: String?,
        @field:Schema(description = "사진 장수. 2 이상이면 칸에 여러 장 표시를 그린다. 사진 게시물이 아니면 0")
        val photoCount: Int,
        @field:Schema(description = "텍스트 게시물 캡션의 앞 100 코드 포인트. 사진과 영상 게시물은 null")
        val captionPreview: String?,
    )

    companion object {
        fun from(page: CursorPageResult<SnsPostGridItemResult>): SnsPostGridPageResponse =
            SnsPostGridPageResponse(
                items =
                    page.items.map {
                        SnsPostGridItemResponse(
                            publicId = it.publicId,
                            mediaType = it.mediaType,
                            thumbnailUrl = it.thumbnailUrl,
                            photoCount = it.photoCount,
                            captionPreview = it.captionPreview,
                        )
                    },
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
