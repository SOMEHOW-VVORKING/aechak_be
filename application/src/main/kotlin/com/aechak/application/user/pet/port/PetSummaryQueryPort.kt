package com.aechak.application.user.pet.port

import com.aechak.application.user.pet.port.view.DefaultPetSummaryView

/** 여러 사용자의 펫 요약을 한 번에 읽는 조회 */
interface PetSummaryQueryPort {
    /** ACTIVE 대표 펫이 있는 사용자만 돌려준다 */
    fun findDefaultPetSummaries(userIds: Collection<Long>): List<DefaultPetSummaryView>
}
