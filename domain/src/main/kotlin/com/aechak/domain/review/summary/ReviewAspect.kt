package com.aechak.domain.review.summary

import com.aechak.domain.review.summary.enums.AspectPolarity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/** AI가 리뷰 한 건에서 뽑은 장단점 하나. quote는 리뷰 노출 본문에서 그대로 떼어 온 문장 */
@Embeddable
class ReviewAspect(
    @Column(name = "aspect_code", length = 30, nullable = false)
    val aspectCode: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "polarity", length = 5, nullable = false)
    val polarity: AspectPolarity,
    @Column(name = "label", length = 100, nullable = false)
    val label: String,
    @Column(name = "quote", length = 500, nullable = false)
    val quote: String,
)
