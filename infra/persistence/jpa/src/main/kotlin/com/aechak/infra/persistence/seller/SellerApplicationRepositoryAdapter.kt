package com.aechak.infra.persistence.seller

import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.repository.SellerApplicationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SellerApplicationJpaRepository : JpaRepository<SellerApplication, Long> {
    fun findByUserId(userId: Long): SellerApplication?

    // 정렬은 JPQL 소유(MySQL은 DESC에서 NULL을 뒤로 보낸다 — 미제출 DRAFT가 목록 끝), id는 동시각 안정 정렬용
    @Query("select a from SellerApplication a where a.status = :status order by a.submittedAt desc, a.id desc")
    fun findPageByStatus(
        status: ApplicationStatus,
        pageable: Pageable,
    ): List<SellerApplication>

    @Query("select a from SellerApplication a order by a.submittedAt desc, a.id desc")
    fun findPageAll(pageable: Pageable): List<SellerApplication>

    fun countByStatus(status: ApplicationStatus): Long

    fun findAllByBusinessRegNo(businessRegNo: String): List<SellerApplication>
}

@Repository
class SellerApplicationRepositoryAdapter(
    private val jpaRepository: SellerApplicationJpaRepository,
) : SellerApplicationRepository {
    override fun save(application: SellerApplication): SellerApplication = jpaRepository.save(application)

    override fun findById(id: Long): SellerApplication? = jpaRepository.findByIdOrNull(id)

    override fun findByUserId(userId: Long): SellerApplication? = jpaRepository.findByUserId(userId)

    override fun findPage(
        status: ApplicationStatus?,
        page: Int,
        size: Int,
    ): List<SellerApplication> {
        val pageable = PageRequest.of(page, size)
        return if (status == null) jpaRepository.findPageAll(pageable) else jpaRepository.findPageByStatus(status, pageable)
    }

    override fun countAll(status: ApplicationStatus?): Long = if (status == null) jpaRepository.count() else jpaRepository.countByStatus(status)

    override fun findAllByBusinessRegNo(businessRegNo: String): List<SellerApplication> = jpaRepository.findAllByBusinessRegNo(businessRegNo)
}
