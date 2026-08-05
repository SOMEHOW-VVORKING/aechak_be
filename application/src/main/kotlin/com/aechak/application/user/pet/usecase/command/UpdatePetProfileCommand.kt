package com.aechak.application.user.pet.usecase.command

import java.math.BigDecimal

data class UpdatePetProfileCommand(
    val userId: Long,
    val petId: Long,
    val name: String,
    val breedId: Long,
    val birthYearMonth: String?,
    val weight: BigDecimal?,
    val profileImageKey: String?,
    val isDefault: Boolean,
    val version: Int?,
)
