package com.aechak.application.user.address.usecase

import com.aechak.application.user.address.usecase.command.AddDeliveryAddressCommand
import com.aechak.application.user.address.usecase.command.UpdateDeliveryAddressCommand
import com.aechak.application.user.address.usecase.result.AddDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.DeleteDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.DeliveryAddressListResult
import com.aechak.application.user.address.usecase.result.SetDefaultDeliveryAddressResult
import com.aechak.application.user.address.usecase.result.UpdateDeliveryAddressResult

interface DeliveryAddressUseCase {
    fun getDeliveryAddresses(userId: Long): DeliveryAddressListResult

    fun addDeliveryAddress(command: AddDeliveryAddressCommand): AddDeliveryAddressResult

    fun updateDeliveryAddress(command: UpdateDeliveryAddressCommand): UpdateDeliveryAddressResult

    fun deleteDeliveryAddress(
        userId: Long,
        addressId: Long,
    ): DeleteDeliveryAddressResult

    fun setDefaultDeliveryAddress(
        userId: Long,
        addressId: Long,
    ): SetDefaultDeliveryAddressResult
}
