package com.aechak.domain.order.claim.enums

/**
 * 클레임 요청 사유 고정 어휘. label은 구매자 선택지와 셀러 화면에 그대로 쓰는 문구다.
 * 유형별로 받을 수 있는 사유는 [selectableFor]가 가른다.
 */
enum class ClaimReasonCode(
    val label: String,
) {
    CHANGE_OF_MIND("단순 변심"),
    WRONG_OPTION("옵션을 잘못 선택했어요"),
    PRICE_MISMATCH("가격 조건이 맞지 않아요"),
    DELIVERY_DELAY("배송이 너무 늦어요"),
    DEFECTIVE("상품이 파손되었거나 불량이에요"),
    WRONG_ITEM("주문과 다른 상품이 왔어요"),
    NOT_AS_DESCRIBED("상품 설명과 달라요"),
    OTHER("기타"),
    ;

    /** 기타는 코드만으로는 판단 근거가 되지 않아 상세 입력을 요구한다. */
    fun requiresDetail(): Boolean = this == OTHER

    fun isSelectableFor(claimType: ClaimType): Boolean = this in selectableFor(claimType)

    companion object {
        /** 유형별로 받을 수 있는 사유. 나열 순서가 화면 선택지 순서다. */
        fun selectableFor(claimType: ClaimType): List<ClaimReasonCode> =
            when (claimType) {
                ClaimType.CANCEL -> listOf(CHANGE_OF_MIND, WRONG_OPTION, PRICE_MISMATCH, DELIVERY_DELAY, OTHER)
                ClaimType.RETURN -> listOf(CHANGE_OF_MIND, DEFECTIVE, WRONG_ITEM, NOT_AS_DESCRIBED, OTHER)
                ClaimType.EXCHANGE -> listOf(DEFECTIVE, WRONG_ITEM, WRONG_OPTION, OTHER)
            }
    }
}
