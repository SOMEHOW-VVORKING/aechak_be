package com.aechak.application.sns.usecase.result

/** post는 상세와 같은 결과이고 repostedBy는 팔로잉 피드에서만 채운다 */
data class SnsFeedItemResult(
    val post: SnsPostDetailResult,
    val repostedBy: SnsFeedReposterResult?,
)

/** 카드 순서 시각에 원글을 리포스트한 사람 */
data class SnsFeedReposterResult(
    val publicId: String,
    val nickname: String,
)
