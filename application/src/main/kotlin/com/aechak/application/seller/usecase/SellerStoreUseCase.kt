package com.aechak.application.seller.usecase

import com.aechak.application.seller.usecase.command.UpdateStoreCommand
import com.aechak.application.seller.usecase.result.StoreSettingsResult

/**
 * 셀러센터 스토어 설정 진입점 계약. 타 BC가 쓰는 SellerUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 셀러센터 하나이고 메서드가 겹치지 않는다.
 */
interface SellerStoreUseCase {
    /** 스토어 설정 화면 데이터 — 셀러가 아니어도 신청 상태를 담아 200으로 내려간다. */
    fun getStore(userId: Long): StoreSettingsResult

    /** 내 스토어 설정 전체 덮어쓰기. 활성 셀러만. */
    fun updateStore(command: UpdateStoreCommand)
}
