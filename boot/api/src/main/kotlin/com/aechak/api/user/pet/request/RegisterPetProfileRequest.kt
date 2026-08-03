package com.aechak.api.user.pet.request

import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class RegisterPetProfileRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = PetProfileConstraints.NAME_MAX, message = "이름은 {max}자를 넘을 수 없습니다.")
    val name: String,
    val breedId: Long,
    @field:Pattern(
        regexp = PetProfileConstraints.BIRTH_YEAR_MONTH_PATTERN,
        message = "생년월은 YYYY 또는 YYYY-MM 형식이어야 합니다.",
    )
    @field:Schema(description = "생년월. 연도만 보내면 서버가 1월로 채워 저장", example = "2022-04")
    val birthYearMonth: String? = null,
    @field:DecimalMin(value = PetProfileConstraints.WEIGHT_MIN, message = "체중은 {value}kg 이상이어야 합니다.")
    @field:DecimalMax(value = PetProfileConstraints.WEIGHT_MAX, message = "체중은 {value}kg 이하여야 합니다.")
    @field:Digits(
        integer = PetProfileConstraints.WEIGHT_INTEGER_DIGITS,
        fraction = PetProfileConstraints.WEIGHT_FRACTION_DIGITS,
        message = "체중은 소수점 첫째자리까지만 입력할 수 있습니다.",
    )
    val weight: BigDecimal? = null,
    @field:Size(max = PetProfileConstraints.IMAGE_KEY_MAX, message = "이미지 키는 {max}자를 넘을 수 없습니다.")
    val profileImageKey: String? = null,
    // Boolean?인 이유: Boolean+기본값이면 명시적 null에서 Jackson 파싱이 깨짐. 생략=false.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean? = null,
) {
    fun toCommand(userId: Long): RegisterPetProfileCommand =
        RegisterPetProfileCommand(
            userId = userId,
            name = name,
            breedId = breedId,
            birthYearMonth = birthYearMonth,
            weight = weight,
            profileImageKey = profileImageKey,
            isDefault = isDefault ?: false,
        )
}
