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
            .select(pet.user.id, breed.label, pet.birthYearMonth)
            .from(pet)
            .join(pet.breed, breed)
            .where(pet.user.id.`in`(userIds), pet.status.eq(PetStatus.ACTIVE))
            // 대표 펫부터 먼저 등록한 순서라 사용자별 첫 행으로 요약한다
            .orderBy(pet.isDefault.desc(), pet.createdAt.asc(), pet.id.asc())
            .fetch()
            .groupBy { checkNotNull(it.get(pet.user.id)) }
            .map { (userId, pets) ->
                val first = pets.first()
                DefaultPetSummaryView(userId, checkNotNull(first.get(breed.label)), first.get(pet.birthYearMonth), pets.size)
            }
}
