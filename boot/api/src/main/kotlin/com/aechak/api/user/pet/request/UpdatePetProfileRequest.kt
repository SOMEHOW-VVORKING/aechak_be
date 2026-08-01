package com.aechak.api.user.pet.request

import com.aechak.application.user.pet.usecase.command.UpdatePetProfileCommand
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/** 전체 객체 전송이라 유지할 값도 실어 보내야 함. `profileImageKey`를 빼면 기존 사진이 지워짐. */
data class UpdatePetProfileRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = 50, message = "이름은 {max}자를 넘을 수 없습니다.")
    val name: String,
    val breedId: Long,
    @field:Pattern(
        regexp = "^\\d{4}(-(0[1-9]|1[0-2]))?$",
        message = "생년월은 YYYY 또는 YYYY-MM 형식이어야 합니다.",
    )
    @field:Schema(description = "생년월. 연도만 보내면 서버가 1월로 채워 저장", example = "2022-04")
    val birthYearMonth: String? = null,
    @field:DecimalMin(value = "0.1", message = "체중은 {value}kg 이상이어야 합니다.")
    @field:DecimalMax(value = "100.0", message = "체중은 {value}kg 이하여야 합니다.")
    @field:Digits(integer = 3, fraction = 1, message = "체중은 소수점 첫째자리까지만 입력할 수 있습니다.")
    val weight: BigDecimal? = null,
    @field:Size(max = 1024, message = "이미지 키는 {max}자를 넘을 수 없습니다.")
    @field:Schema(description = "새로 올린 tmp 키 또는 유지할 기존 키. 빼면 사진이 지워진다")
    val profileImageKey: String? = null,
    // 생략=변경 없음. 대표 해제는 다른 펫 지정으로만 됨.
    // Boolean?인 이유: Boolean+기본값이면 명시적 null에서 Jackson 파싱이 깨짐.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean? = null,
    @field:Schema(description = "목록·수정 응답에서 받은 값. 보내면 그 사이 누가 먼저 고쳤는지 검사한다")
    val version: Int? = null,
) {
    fun toCommand(
        userId: Long,
        petId: Long,
    ): UpdatePetProfileCommand =
        UpdatePetProfileCommand(
            userId = userId,
            petId = petId,
            name = name,
            breedId = breedId,
            birthYearMonth = birthYearMonth,
            weight = weight,
            profileImageKey = profileImageKey,
            isDefault = isDefault ?: false,
            version = version,
        )
}
