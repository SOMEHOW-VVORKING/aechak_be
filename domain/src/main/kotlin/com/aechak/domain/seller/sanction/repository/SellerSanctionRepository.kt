package com.aechak.domain.seller.sanction.repository

import com.aechak.domain.seller.sanction.SellerSanction

/**
 * 판매정지 이력 포트. 추가 전용 기록이라 쓰기는 save 하나만 연다 —
 * 사후에 고칠 수 있는 기록은 분쟁에서 증거가 되지 못한다.
 * 운영 화면의 이력 조회는 읽기 모델 포트(AdminSellerQueryPort)가 담당한다.
 */
interface SellerSanctionRepository {
    fun save(sanction: SellerSanction): SellerSanction
}
