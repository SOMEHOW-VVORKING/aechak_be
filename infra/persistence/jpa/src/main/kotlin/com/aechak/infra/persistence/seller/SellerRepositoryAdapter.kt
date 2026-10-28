package com.aechak.infra.persistence.seller

import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SellerJpaRepository : JpaRepository<Seller, Long> {
    // 상태 한 칼럼만 읽는다 — 자격 게이트가 셀러센터 요청마다 타는 길이라 엔티티(배송정책 TEXT·이미지 키·주소)를
    // 통째로 올리지 않는다. 같은 패키지 SellerApplicationJpaRepository.findStatusByUserId와 같은 모양.
    @Query("select s.status from Seller s where s.userId = :userId")
    fun findStatusByUserId(userId: Long): SellerStatus?
}

@Repository
class SellerRepositoryAdapter(
    private val jpaRepository: SellerJpaRepository,
) : SellerRepository {
    // sellers는 user_id가 PK — 존재 검사가 곧 기셀러 판정
    override fun existsByUserId(userId: Long): Boolean = jpaRepository.existsById(userId)

    override fun save(seller: Seller): Seller = jpaRepository.save(seller)

    override fun findStatusByUserId(userId: Long): SellerStatus? = jpaRepository.findStatusByUserId(userId)

    override fun findByUserId(userId: Long): Seller? = jpaRepository.findById(userId).orElse(null)
}
