package com.aechak.api.user.pet

import com.aechak.api.user.pet.response.BreedResponse
import com.aechak.application.user.pet.usecase.BreedUseCase
import com.aechak.domain.user.pet.enums.Species
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/breeds")
class BreedController(
    private val breedUseCase: BreedUseCase,
) {
    @GetMapping
    fun getBreeds(
        @RequestParam species: Species,
    ): ResponseEntity<ApiResponse<List<BreedResponse>>> =
        ResponseEntity.ok(ApiResponse.of(breedUseCase.getBreeds(species).map(BreedResponse::from)))
}
