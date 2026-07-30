package com.aechak.infra.persistence.user.term

import com.aechak.domain.user.term.Term
import com.aechak.domain.user.term.repository.TermRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface TermJpaRepository : JpaRepository<Term, Long> {
    fun findAllByIsActiveTrueOrderByIdAsc(): List<Term>
}

@Repository
class TermRepositoryAdapter(
    private val jpaRepository: TermJpaRepository,
) : TermRepository {
    override fun findAllActiveOrderedById(): List<Term> = jpaRepository.findAllByIsActiveTrueOrderByIdAsc()
}
