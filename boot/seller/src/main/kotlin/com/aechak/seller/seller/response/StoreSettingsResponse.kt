package com.aechak.seller.seller.response

import com.aechak.application.seller.usecase.result.StoreResult
import com.aechak.application.seller.usecase.result.StoreSettingsResult
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.enums.TelesalesNoticeStatus

/** 아직 셀러가 아니면 store = null이고 applicationStatus로 입점 신청 단계를 그린다. */
data class StoreSettingsResponse(
    val isSeller: Boolean,
    val applicationStatus: ApplicationStatus?,
    val store: StoreResponse?,
) {
    companion object {
        fun from(result: StoreSettingsResult): StoreSettingsResponse =
            StoreSettingsResponse(
                isSeller = result.isSeller,
                applicationStatus = result.applicationStatus,
                store = result.store?.let(StoreResponse::from),
            )
    }
}

data class StoreResponse(
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
        fun from(result: StoreResult): StoreResponse =
            StoreResponse(
                sellerId = result.sellerId,
                status = result.status,
                storeName = result.storeName,
                brandIntro = result.brandIntro,
                profileImageUrl = result.profileImageUrl,
                profileImageKey = result.profileImageKey,
                bannerImageUrl = result.bannerImageUrl,
                bannerImageKey = result.bannerImageKey,
                zipCode = result.zipCode,
                roadAddress = result.roadAddress,
                detailAddress = result.detailAddress,
                confirmedOrderCount = result.confirmedOrderCount,
                telesalesNoticeStatus = result.telesalesNoticeStatus,
            )
    }
}
