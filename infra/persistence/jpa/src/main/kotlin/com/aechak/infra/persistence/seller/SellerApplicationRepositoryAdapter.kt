package com.aechak.infra.persistence.seller

import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.repository.SellerApplicationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SellerApplicationJpaRepository : JpaRepository<SellerApplication, Long> {
    fun findByUserId(userId: Long): SellerApplication?
}

@Repository
class SellerApplicationRepositoryAdapter(
    private val jpaRepository: SellerApplicationJpaRepository,
) : SellerApplicationRepository {
    override fun save(application: SellerApplication): SellerApplication = jpaRepository.save(application)

    override fun findById(id: Long): SellerApplication? = jpaRepository.findByIdOrNull(id)

    override fun findByUserId(userId: Long): SellerApplication? = jpaRepository.findByUserId(userId)
}
