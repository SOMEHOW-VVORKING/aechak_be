package com.aechak.infra.persistence.inquiry

import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.repository.InquiryRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface InquiryJpaRepository : JpaRepository<Inquiry, Long> {
    // 쓰기 경로 전용. 단일 테이블 조회로 둬서 문의 행만 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.id = :id")
    fun findForUpdateById(
        @Param("id") id: Long,
    ): Inquiry?
}

@Repository
class InquiryRepositoryAdapter(
    private val jpaRepository: InquiryJpaRepository,
) : InquiryRepository {
    override fun save(inquiry: Inquiry): Inquiry = jpaRepository.save(inquiry)

    override fun findById(inquiryId: Long): Inquiry? = jpaRepository.findById(inquiryId).orElse(null)

    override fun findByIdForUpdate(inquiryId: Long): Inquiry? = jpaRepository.findForUpdateById(inquiryId)
}
