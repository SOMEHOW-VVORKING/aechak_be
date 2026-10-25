package com.aechak.application.user.pet.port

import com.aechak.application.user.pet.port.view.DefaultPetSummaryView

/** 여러 사용자의 펫 요약을 한 번에 읽는 조회 */
interface PetSummaryQueryPort {
    /** ACTIVE 펫이 있는 사용자만 돌려준다. 대표 펫이 여럿이면 먼저 등록한 대표 펫, 없으면 먼저 등록한 펫으로 요약한다 */
    fun findDefaultPetSummaries(userIds: Collection<Long>): List<DefaultPetSummaryView>
}
