package com.aechak.application.inspection.port.view

import com.aechak.domain.product.product.enums.InspectionStatus

/** 관리자가 판정과 함께 보는 상품 본문. AI가 받은 것과 같은 재료임 */
data class ReviewTargetProductView(
    val productId: Long,
    val productPublicId: String,
    val productName: String,
    val sellerName: String,
    /** 대분류부터 소분류 순 */
    val categoryNames: List<String>,
    val description: String?,
    val representativeImageKey: String?,
    /** 그룹명 뒤에 그 그룹의 값 이름들을 붙여 늘어놓음 */
    val optionNames: List<String>,
    val inspectionStatus: InspectionStatus,
)
