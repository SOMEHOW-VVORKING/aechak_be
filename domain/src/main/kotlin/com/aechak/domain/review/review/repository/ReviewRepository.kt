package com.aechak.domain.review.review.repository

import com.aechak.domain.review.review.Review

interface ReviewRepository {
    fun findById(id: Long): Review?

    fun save(review: Review): Review
}
