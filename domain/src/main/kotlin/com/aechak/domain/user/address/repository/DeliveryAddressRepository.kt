package com.aechak.domain.user.address.repository

import com.aechak.domain.user.address.DeliveryAddress

interface DeliveryAddressRepository {
    fun save(address: DeliveryAddress): DeliveryAddress

    fun findActiveById(id: Long): DeliveryAddress?

    fun findAllActiveByUserIdOrderByUpdatedAtDesc(userId: Long): List<DeliveryAddress>

    fun countActiveByUserId(userId: Long): Long
}
