package com.aechak.api.user.pet

import com.aechak.application.user.pet.usecase.BreedUseCase
import com.aechak.application.user.pet.usecase.result.BreedResult
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.pet.enums.Species
import com.aechak.webcommon.error.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class BreedControllerTest {
    private val fakeUseCase =
        object : BreedUseCase {
            override fun getBreeds(species: Species): List<BreedResult> = listOf(BreedResult(breedId = 1L, label = "골든 리트리버"))
        }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(BreedController(fakeUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `품종을 조회하면 200과 품종 목록을 반환한다`() {
        mockMvc
            .perform(get("/breeds").param("species", "DOG"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].breedId").value(1))
            .andExpect(jsonPath("$.data[0].label").value("골든 리트리버"))
    }

    @Test
    fun `species 파라미터가 누락되면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/breeds"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `species가 유효하지 않은 값이면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/breeds").param("species", "BIRD"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }
}
