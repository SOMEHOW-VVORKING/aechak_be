package com.aechak.domain.review.summary.enums

enum class ReviewExtractionStatus {
    EXTRACTED,

    /** AI가 읽지 못했거나 입력에서 뺐음. 장단점이 없는 리뷰와 다름 */
    UNEXTRACTED,
}
