package com.aechak.application.review.port.view

data class ReviewImageView(
    val reviewId: Long,
    val storageKey: String,
    val sortOrder: Int,
)
