package com.aechak.application.inspection.port

/**
 * 이의 접수가 상품 행을 잠그고 받아 오는 것.
 *
 * 노출 상태를 그대로 넘기지 않고 "관리자가 반려했는가"만 돌려준다. 포트의 규약이 "검수는
 * 막아라·되돌려라까지만 말하고 그것이 어떤 상태 전이인지는 상품 쪽이 정한다"이고,
 * 접수 자격이 알아야 하는 것은 그 한 가지뿐이다.
 */
data class ProductAppealTarget(
    val productName: String,
    val sellerId: Long,
    /** 관리자가 반려해 노출이 멈춘 상태. 열린 검토 행 없이도 이의를 받는 유일한 경우 */
    val adminRejected: Boolean,
)
