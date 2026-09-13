package com.aechak.domain.user.point.policy

/** 리뷰 유형에 따른 포인트 적립 정책. */
object ReviewRewardPolicy {
    private const val TEXT_REWARD = 300L
    private const val PHOTO_REWARD = 500L

    fun amountFor(hasPhoto: Boolean): Long = if (hasPhoto) PHOTO_REWARD else TEXT_REWARD
}
