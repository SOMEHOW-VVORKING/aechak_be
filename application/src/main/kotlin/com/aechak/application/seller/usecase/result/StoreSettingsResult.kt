package com.aechak.application.seller.usecase.result

import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.enums.TelesalesNoticeStatus

/**
 * 스토어 설정 화면 1회 조회 결과. 한 화면이 입점 신청과 스토어 운영을 겸하므로
 * 아직 셀러가 아닌 경우(store = null)와 셀러인 경우를 한 응답으로 표현한다.
 * applicationStatus는 신청 이력이 없으면 null.
 */
data class StoreSettingsResult(
    val isSeller: Boolean,
    val applicationStatus: ApplicationStatus?,
    val store: StoreResult?,
) {
    companion object {
        fun of(
            seller: Seller?,
            applicationStatus: ApplicationStatus?,
            profileImageUrl: String?,
            bannerImageUrl: String?,
        ): StoreSettingsResult =
            StoreSettingsResult(
                isSeller = seller != null,
                applicationStatus = applicationStatus,
                store = seller?.let { StoreResult.from(it, profileImageUrl, bannerImageUrl) },
            )
    }
}

/** 이미지 URL은 저장 키를 공개 base URL에 붙여 조립한 값이라 호출부가 넘긴다. */
data class StoreResult(
    val sellerId: Long,
    val status: SellerStatus,
    val storeName: String,
    val brandIntro: String?,
    val profileImageUrl: String?,
    val profileImageKey: String?,
    val bannerImageUrl: String?,
    val bannerImageKey: String?,
    val zipCode: String?,
    val roadAddress: String?,
    val detailAddress: String?,
    val confirmedOrderCount: Int,
    val telesalesNoticeStatus: TelesalesNoticeStatus,
) {
    companion object {
        fun from(
            seller: Seller,
            profileImageUrl: String?,
            bannerImageUrl: String?,
        ): StoreResult =
            StoreResult(
                sellerId = seller.userId,
                status = seller.status,
                storeName = seller.storeName,
                brandIntro = seller.brandIntro,
                profileImageUrl = profileImageUrl,
                profileImageKey = seller.profileImageKey,
                bannerImageUrl = bannerImageUrl,
                bannerImageKey = seller.bannerImageKey,
                zipCode = seller.zipCode,
                roadAddress = seller.roadAddress,
                detailAddress = seller.detailAddress,
                confirmedOrderCount = seller.confirmedOrderCount,
                telesalesNoticeStatus = seller.telesalesNoticeStatus,
            )
    }
}
