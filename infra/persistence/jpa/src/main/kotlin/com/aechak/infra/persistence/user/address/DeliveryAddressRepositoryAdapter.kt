package com.aechak.infra.persistence.user.address

import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.domain.user.address.enums.DeliveryAddressStatus
import com.aechak.domain.user.address.repository.DeliveryAddressRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface DeliveryAddressJpaRepository : JpaRepository<DeliveryAddress, Long> {
    fun findByIdAndStatus(
        id: Long,
        status: DeliveryAddressStatus,
    ): DeliveryAddress?

    fun findAllByUserIdAndStatusOrderByUpdatedAtDescIdDesc(
        userId: Long,
        status: DeliveryAddressStatus,
    ): List<DeliveryAddress>

    @Query(
        "select d from DeliveryAddress d " +
            "where d.user.id = :userId and d.status = :status " +
            "order by d.isDefault desc, d.updatedAt desc, d.id desc",
    )
    fun findActiveByUserIdDefaultFirst(
        @Param("userId") userId: Long,
        @Param("status") status: DeliveryAddressStatus,
    ): List<DeliveryAddress>

    fun countByUserIdAndStatus(
        userId: Long,
        status: DeliveryAddressStatus,
    ): Long
}

@Repository
class DeliveryAddressRepositoryAdapter(
    private val jpaRepository: DeliveryAddressJpaRepository,
) : DeliveryAddressRepository {
    override fun save(address: DeliveryAddress): DeliveryAddress = jpaRepository.save(address)

    override fun findActiveById(id: Long): DeliveryAddress? = jpaRepository.findByIdAndStatus(id, DeliveryAddressStatus.ACTIVE)

    override fun findAllActiveByUserIdDefaultFirst(userId: Long): List<DeliveryAddress> =
        jpaRepository.findActiveByUserIdDefaultFirst(userId, DeliveryAddressStatus.ACTIVE)

    override fun findAllActiveByUserIdOrderByUpdatedAtDesc(userId: Long): List<DeliveryAddress> =
        jpaRepository.findAllByUserIdAndStatusOrderByUpdatedAtDescIdDesc(userId, DeliveryAddressStatus.ACTIVE)

    override fun countActiveByUserId(userId: Long): Long = jpaRepository.countByUserIdAndStatus(userId, DeliveryAddressStatus.ACTIVE)
}
