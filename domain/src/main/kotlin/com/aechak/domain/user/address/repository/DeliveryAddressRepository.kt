package com.aechak.domain.user.address.repository

import com.aechak.domain.user.address.DeliveryAddress

interface DeliveryAddressRepository {
    fun save(address: DeliveryAddress): DeliveryAddress

    fun findActiveById(id: Long): DeliveryAddress?

    /** 표시용 — 기본 배송지 최상단 고정, 그 아래는 최신순. 승격 대상 선정과 정렬 의도가 달라 분리한다. */
    fun findAllActiveByUserIdDefaultFirst(userId: Long): List<DeliveryAddress>

    /** 승격/해제용 — 최신순. 기본 삭제 시 이 순서로 승격 후보를 고른다(BR-002). */
    fun findAllActiveByUserIdOrderByUpdatedAtDesc(userId: Long): List<DeliveryAddress>

    fun countActiveByUserId(userId: Long): Long
}
