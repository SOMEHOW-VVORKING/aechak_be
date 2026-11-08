package com.aechak.infra.persistence.user.credential

import com.aechak.domain.user.credential.AdminCredential
import com.aechak.domain.user.credential.repository.AdminCredentialRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface AdminCredentialJpaRepository : JpaRepository<AdminCredential, Long> {
    fun findByLoginId(loginId: String): AdminCredential?
}

@Repository
class AdminCredentialRepositoryAdapter(
    private val jpaRepository: AdminCredentialJpaRepository,
) : AdminCredentialRepository {
    override fun findByLoginId(loginId: String): AdminCredential? = jpaRepository.findByLoginId(loginId)

    override fun save(credential: AdminCredential): AdminCredential = jpaRepository.save(credential)
}
