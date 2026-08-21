package com.aechak.infra.persistence.inquiry

import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.repository.InquiryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface InquiryJpaRepository : JpaRepository<Inquiry, Long>

@Repository
class InquiryRepositoryAdapter(
    private val jpaRepository: InquiryJpaRepository,
) : InquiryRepository {
    override fun save(inquiry: Inquiry): Inquiry = jpaRepository.save(inquiry)
}
