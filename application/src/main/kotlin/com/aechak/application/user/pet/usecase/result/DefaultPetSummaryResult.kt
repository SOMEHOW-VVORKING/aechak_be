package com.aechak.application.user.pet.usecase.result

import com.aechak.application.user.pet.port.view.DefaultPetSummaryView

/** 사용자별 대표 펫 요약 */
data class DefaultPetSummaryResult(
    val userId: Long,
    val breedLabel: String,
    val birthYearMonth: String?,
    val activePetCount: Int,
) {
    companion object {
        fun from(view: DefaultPetSummaryView): DefaultPetSummaryResult =
            DefaultPetSummaryResult(view.userId, view.breedLabel, view.birthYearMonth, view.activePetCount)
    }
}
