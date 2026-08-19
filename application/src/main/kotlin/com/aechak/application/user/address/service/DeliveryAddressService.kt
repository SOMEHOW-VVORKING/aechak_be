package com.aechak.application.user.address.service

import com.aechak.application.user.address.usecase.command.AddDeliveryAddressCommand
import com.aechak.application.user.address.usecase.command.UpdateDeliveryAddressCommand
import com.aechak.application.user.user.service.UserService
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.domain.user.address.repository.DeliveryAddressRepository
import com.aechak.domain.user.error.UserErrorCode
import org.springframework.stereotype.Service

@Service
class DeliveryAddressService(
    private val deliveryAddressRepository: DeliveryAddressRepository,
    private val userService: UserService,
) {
    fun getActiveDeliveryAddresses(userId: Long): List<DeliveryAddress> =
        deliveryAddressRepository.findAllActiveByUserIdDefaultFirst(userId)

    fun countActive(userId: Long): Long = deliveryAddressRepository.countActiveByUserId(userId)

    fun add(command: AddDeliveryAddressCommand): DeliveryAddress {
        // 완벽한 보장은 안됨. 문제 시 비관적 락.
        val activeCount = deliveryAddressRepository.countActiveByUserId(command.userId)
        if (activeCount >= MAX_ACTIVE_DELIVERY_ADDRESSES) {
            throw BusinessException(UserErrorCode.DELIVERY_ADDRESS_LIMIT_EXCEEDED)
        }
        val user = userService.getById(command.userId)
        if (command.isDefault) {
            releaseOtherDefaults(command.userId, exceptId = null)
        }
        val deliveryAddress = command.toEntity(user, encodeContact(command.contactNumber))
        if (activeCount == 0L) {
            deliveryAddress.markAsDefault()
        }
        return deliveryAddressRepository.save(deliveryAddress)
    }

    fun update(command: UpdateDeliveryAddressCommand): DeliveryAddress {
        val address = loadOwnedActive(command.userId, command.addressId)
        address.update(
            receiverName = command.receiverName,
            contactNumber = encodeContact(command.contactNumber),
            zipCode = command.zipCode,
            baseAddress = command.baseAddress,
            detailAddress = command.detailAddress,
            deliveryMemo = command.deliveryMemo,
            label = command.label,
        )

        if (command.isDefault) {
            promoteToDefault(command.userId, address)
        }
        return deliveryAddressRepository.save(address)
    }

    /** 삭제로 기본이 비면 하나를 자동 승격하고 그 id를 돌려줌. 승격이 없었으면 null. */
    fun delete(
        userId: Long,
        addressId: Long,
    ): Long? {
        val address = loadOwnedActive(userId, addressId)
        val wasDefault = address.isDefault
        address.delete()
        deliveryAddressRepository.save(address)
        if (!wasDefault) return null

        val promoted =
            deliveryAddressRepository
                .findAllActiveByUserIdOrderByUpdatedAtDesc(userId)
                .firstOrNull { it.id != addressId } // 혹시 flush 가 늦어도 id로 삭제한 address는 거름
                ?: return null
        promoted.markAsDefault()
        deliveryAddressRepository.save(promoted)
        return promoted.id
    }

    fun setDefault(
        userId: Long,
        addressId: Long,
    ): DeliveryAddress {
        val address = loadOwnedActive(userId, addressId)
        promoteToDefault(userId, address)
        return deliveryAddressRepository.save(address)
    }

    /** 내 배송지인지 확인*/
    fun loadOwnedActive(
        userId: Long,
        addressId: Long,
    ): DeliveryAddress {
        val address =
            deliveryAddressRepository.findActiveById(addressId)
                ?: throw BusinessException(UserErrorCode.DELIVERY_ADDRESS_NOT_FOUND)
        if (address.user.id != userId) {
            throw BusinessException(UserErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED)
        }
        return address
    }

    /**
     * 기본 배송지를 바꾸는 곳
     */
    private fun promoteToDefault(
        userId: Long,
        address: DeliveryAddress,
    ) {
        releaseOtherDefaults(userId, exceptId = address.id)
        address.markAsDefault()
    }

    /**
     * 대상(exceptId)만 빼고 이 유저의 기본을 전부 없앰.
     * 완벽한 동시성 방어는 불가능하지만, 불필요하다고 판단함.
     * save가 updatedAt을 건드려서 내려간 옛 기본이 목록 맨 위로 올라오는 부작용이 있다. 거슬리면 벌크 UPDATE로.
     */
    private fun releaseOtherDefaults(
        userId: Long,
        exceptId: Long?,
    ) {
        deliveryAddressRepository
            .findAllActiveByUserIdOrderByUpdatedAtDesc(userId)
            .filter { it.isDefault && it.id != exceptId }
            .forEach {
                it.releaseDefault()
                deliveryAddressRepository.save(it)
            }
    }

    // 연락처는 아직 평문으로 저장한다(암호화는 결정 대기). 평문을 만지는 곳은 이 두 함수뿐이어야 한다.
    fun decodeContact(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    private fun encodeContact(raw: String): ByteArray = raw.toByteArray(Charsets.UTF_8)

    companion object {
        const val MAX_ACTIVE_DELIVERY_ADDRESSES = 10
    }
}
