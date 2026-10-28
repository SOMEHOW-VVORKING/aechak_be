package com.aechak.application.user.pet.port.view

/** 사용자별 대표 펫 품종과 생년월, ACTIVE 펫 수(대표 포함) */
data class DefaultPetSummaryView(
    val userId: Long,
    val breedLabel: String,
    val birthYearMonth: String?,
    val activePetCount: Int,
)
