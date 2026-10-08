package com.aechak.domain.user.point.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewRewardPolicyTest {
    @Test
    fun `텍스트 리뷰는 300포인트를 적립한다`() {
        assertEquals(300L, ReviewRewardPolicy.amountFor(hasPhoto = false))
    }

    @Test
    fun `포토 리뷰는 500포인트를 적립한다`() {
        assertEquals(500L, ReviewRewardPolicy.amountFor(hasPhoto = true))
    }
}
