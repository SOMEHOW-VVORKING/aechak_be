package com.aechak.infra.persistence.audit

import com.aechak.domain.audit.auditlog.AdminAuditLog
import com.aechak.domain.audit.auditlog.repository.AdminAuditLogRepository
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Repository as SpringRepository

/**
 * Spring Data 인터페이스를 JpaRepository가 아니라 마커 [Repository]로 좁힌다 —
 * JpaRepository를 상속하면 delete·deleteAll·saveAndFlush가 전부 빈에 생겨,
 * 수정 경로를 만들지 않겠다는 포트의 약속이 한 줄 호출로 우회된다.
 */
interface AdminAuditLogJpaRepository : Repository<AdminAuditLog, Long> {
    fun save(log: AdminAuditLog): AdminAuditLog
}

@SpringRepository
class AdminAuditLogRepositoryAdapter(
    private val jpaRepository: AdminAuditLogJpaRepository,
) : AdminAuditLogRepository {
    override fun append(log: AdminAuditLog): AdminAuditLog = jpaRepository.save(log)
}
