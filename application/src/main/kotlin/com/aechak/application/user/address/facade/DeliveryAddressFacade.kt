package com.aechak.application.user.address.facade

import com.aechak.application.user.address.service.DeliveryAddressService
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
import com.aechak.application.user.address.usecase.command.AddDeliveryAddressCommand
import com.aechak.application.user.address.usecase.command.UpdateDeliveryAddressCommand
import com.aechak.application.user.address.usecase.result.AddDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.DeleteDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.DeliveryAddressListResult
import com.aechak.application.user.address.usecase.result.DeliveryAddressResult
import com.aechak.application.user.address.usecase.result.SetDefaultDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.UpdateDeliveryAddressResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeliveryAddressFacade(
    private val deliveryAddressService: DeliveryAddressService,
) : DeliveryAddressUseCase {
    @Transactional(readOnly = true)
    override fun getDeliveryAddresses(userId: Long): DeliveryAddressListResult {
        val addresses = deliveryAddressService.getActiveDeliveryAddresses(userId)
        val results =
            addresses.map {
                DeliveryAddressResult.from(it, deliveryAddressService.decodeContact(it.contactNumber))
            }
        return DeliveryAddressListResult(results, addresses.size)
    }

    @Transactional
    override fun addDeliveryAddress(command: AddDeliveryAddressCommand): AddDeliveryAddressResult {
        val saved = deliveryAddressService.add(command)
        return AddDeliveryAddressResult.from(saved, deliveryAddressService.countActive(command.userId).toInt())
    }

    @Transactional
    override fun updateDeliveryAddress(command: UpdateDeliveryAddressCommand): UpdateDeliveryAddressResult {
        val saved = deliveryAddressService.update(command)
        return UpdateDeliveryAddressResult.from(saved)
    }

    @Transactional
    override fun deleteDeliveryAddress(
        userId: Long,
        addressId: Long,
    ): DeleteDeliveryAddressResult = DeleteDeliveryAddressResult(deliveryAddressService.delete(userId, addressId))

    @Transactional
    override fun setDefaultDeliveryAddress(
        userId: Long,
        addressId: Long,
    ): SetDefaultDeliveryAddressResult {
        val saved = deliveryAddressService.setDefault(userId, addressId)
        return SetDefaultDeliveryAddressResult.from(saved)
    }
}
