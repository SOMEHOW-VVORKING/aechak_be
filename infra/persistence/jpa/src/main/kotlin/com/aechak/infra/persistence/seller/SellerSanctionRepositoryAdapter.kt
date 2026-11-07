package com.aechak.infra.persistence.seller

import com.aechak.domain.seller.sanction.SellerSanction
import com.aechak.domain.seller.sanction.repository.SellerSanctionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SellerSanctionJpaRepository : JpaRepository<SellerSanction, Long>

@Repository
class SellerSanctionRepositoryAdapter(
    private val jpaRepository: SellerSanctionJpaRepository,
) : SellerSanctionRepository {
    override fun save(sanction: SellerSanction): SellerSanction = jpaRepository.save(sanction)
}
