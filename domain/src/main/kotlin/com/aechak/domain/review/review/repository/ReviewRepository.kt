package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    /** 저장하고 즉시 flush한다. */
    fun save(review: Review): Review

    fun existsByOrderItemId(orderItemId: Long): Boolean
}
