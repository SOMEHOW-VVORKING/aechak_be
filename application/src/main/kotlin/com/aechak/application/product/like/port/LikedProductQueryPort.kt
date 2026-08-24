package com.aechak.application.product.like.port

import com.aechak.application.product.like.port.view.LikedProductView

interface LikedProductQueryPort {
    fun findLikedPage(condition: LikedProductCondition): List<LikedProductView>

    fun countLiked(userId: Long): Long
}
