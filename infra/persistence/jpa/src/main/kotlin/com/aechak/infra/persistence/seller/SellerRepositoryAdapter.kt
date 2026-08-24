package com.aechak.infra.persistence.seller

import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SellerJpaRepository : JpaRepository<Seller, Long> {
    fun existsByUserIdAndStatus(
        userId: Long,
        status: SellerStatus,
    ): Boolean
}

@Repository
class SellerRepositoryAdapter(
    private val jpaRepository: SellerJpaRepository,
) : SellerRepository {
    // sellers는 user_id가 PK — 존재 검사가 곧 기셀러 판정
    override fun existsByUserId(userId: Long): Boolean = jpaRepository.existsById(userId)

    override fun existsActiveByUserId(userId: Long): Boolean = jpaRepository.existsByUserIdAndStatus(userId, SellerStatus.ACTIVE)
}
