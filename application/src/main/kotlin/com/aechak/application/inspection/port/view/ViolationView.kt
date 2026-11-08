package com.aechak.application.inspection.port.view

/** 저장된 위반 한 건. [category]가 문자열인 이유는 모르는 분류 하나로 행 전체를 못 읽게 되기 때문임 */
data class ViolationView(
    val category: String,
    val spanStart: Int?,
    val quote: String?,
    val suggestion: String?,
)
