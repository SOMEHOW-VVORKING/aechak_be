package com.aechak.domain.user.pet.repository

import com.aechak.domain.user.pet.PetProfile

interface PetProfileRepository {
    fun save(pet: PetProfile): PetProfile

    fun findActiveById(id: Long): PetProfile?

    /** 표시용. 기본 펫 최상단, 그 아래 등록순. breed까지 적재해 돌려줌 */
    fun findAllActiveByUserIdDefaultFirst(userId: Long): List<PetProfile>

    /** 승격 후보 선정용. 최근순, 표시 정렬과 의도가 달라 분리 */
    fun findAllActiveByUserIdRecentFirst(userId: Long): List<PetProfile>

    fun countActiveByUserId(userId: Long): Long
}
