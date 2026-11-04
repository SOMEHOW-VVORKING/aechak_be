package com.aechak.api.sns.request

import io.swagger.v3.oas.annotations.media.Schema
import org.hibernate.validator.constraints.Range

/** query는 검증하지 않는다. 표기 정리와 글자 규칙 판정은 application이 하고 400 대신 상태로 알린다 */
data class HashtagSearchRequest(
    @field:Schema(description = "모달 입력값 그대로. 생략하거나 정리한 값이 비면 추천 태그다", example = "댕")
    val query: String? = null,
    @field:Range(min = 1, max = 50, message = "size는 1~50 사이여야 합니다.")
    val size: Int = 20,
)
