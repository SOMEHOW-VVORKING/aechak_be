package com.aechak.seller.seller.request

import com.aechak.application.seller.usecase.command.UpdateStoreCommand
import com.aechak.domain.seller.seller.Seller
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 스토어 설정 전체 덮어쓰기 요청. 바디에 없는 항목은 비운 것으로 본다 —
 * 이미지를 빼면 제거, 주소를 빼면 비워진다.
 */
data class StoreSettingsUpdateRequest(
    @field:NotBlank(message = "상호(스토어명)는 필수입니다.")
    @field:Size(max = Seller.MAX_STORE_NAME_LENGTH, message = "상호(스토어명)는 {max}자 이하여야 합니다.")
    val storeName: String,
    @field:Size(max = Seller.MAX_BRAND_INTRO_LENGTH, message = "브랜드 소개는 {max}자 이하여야 합니다.")
    val brandIntro: String? = null,
    @field:Size(max = Seller.MAX_IMAGE_KEY_LENGTH, message = "프로필 이미지 키는 {max}자 이하여야 합니다.")
    val profileImageKey: String? = null,
    @field:Size(max = Seller.MAX_IMAGE_KEY_LENGTH, message = "배너 이미지 키는 {max}자 이하여야 합니다.")
    val bannerImageKey: String? = null,
    @field:Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    val zipCode: String? = null,
    @field:Size(max = 255, message = "도로명주소는 255자 이하여야 합니다.")
    val roadAddress: String? = null,
    @field:Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
    val detailAddress: String? = null,
) {
    fun toCommand(sellerId: Long): UpdateStoreCommand =
        UpdateStoreCommand(
            sellerId = sellerId,
            // 구매자에게 그대로 보이는 이름이라 앞뒤 공백은 떼고 저장한다
            storeName = storeName.trim(),
            brandIntro = brandIntro,
            profileImageKey = profileImageKey,
            bannerImageKey = bannerImageKey,
            zipCode = zipCode,
            roadAddress = roadAddress,
            detailAddress = detailAddress,
        )
}
