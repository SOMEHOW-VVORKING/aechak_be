package com.aechak.infra.persistence.user.pet

import com.aechak.application.user.pet.port.PetSummaryQueryPort
import com.aechak.application.user.pet.port.view.DefaultPetSummaryView
import com.aechak.domain.user.pet.QBreed
import com.aechak.domain.user.pet.QPetProfile
import com.aechak.domain.user.pet.enums.PetStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val pet = QPetProfile.petProfile
private val breed = QBreed.breed

/** 사용자들의 ACTIVE 펫을 한 번에 읽어 사용자별 대표 펫과 수로 묶는다 */
@Repository
class PetSummaryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : PetSummaryQueryPort {
    override fun findDefaultPetSummaries(userIds: Collection<Long>): List<DefaultPetSummaryView> =
        queryFactory
            .select(pet.user.id, pet.isDefault, breed.label, pet.birthYearMonth)
            .from(pet)
            .join(pet.breed, breed)
            .where(pet.user.id.`in`(userIds), pet.status.eq(PetStatus.ACTIVE))
            .fetch()
            .groupBy { checkNotNull(it.get(pet.user.id)) }
            .mapNotNull { (userId, pets) ->
                pets.firstOrNull { it.get(pet.isDefault) == true }?.let {
                    DefaultPetSummaryView(userId, checkNotNull(it.get(breed.label)), it.get(pet.birthYearMonth), pets.size)
                }
            }
}
