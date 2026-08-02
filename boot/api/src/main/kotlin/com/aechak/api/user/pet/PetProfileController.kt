package com.aechak.api.user.pet

import com.aechak.api.user.pet.request.RegisterPetProfileRequest
import com.aechak.api.user.pet.response.PetProfileListResponse
import com.aechak.api.user.pet.response.RegisterPetProfileResponse
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me/pets") // 접두(api.base-path)는 WebConfig가 일괄 부착
class PetProfileController(
    private val petProfileUseCase: PetProfileUseCase,
) {
    @GetMapping
    fun getPets(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<PetProfileListResponse>> =
        ResponseEntity.ok(ApiResponse.of(PetProfileListResponse.from(petProfileUseCase.getPets(principal.userId))))

    @PostMapping
    fun registerPet(
        @Valid @RequestBody request: RegisterPetProfileRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<RegisterPetProfileResponse>> {
        val result = petProfileUseCase.registerPet(request.toCommand(principal.userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(RegisterPetProfileResponse.from(result)))
    }
}
