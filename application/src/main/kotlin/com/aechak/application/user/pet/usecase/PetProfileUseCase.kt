package com.aechak.application.user.pet.usecase

import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.aechak.application.user.pet.usecase.command.UpdatePetProfileCommand
import com.aechak.application.user.pet.usecase.result.PetProfileListResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.application.user.pet.usecase.result.RegisterPetProfileResult

interface PetProfileUseCase {
    fun registerPet(command: RegisterPetProfileCommand): RegisterPetProfileResult

    fun getPets(userId: Long): PetProfileListResult

    fun updatePet(command: UpdatePetProfileCommand): PetProfileResult
}
