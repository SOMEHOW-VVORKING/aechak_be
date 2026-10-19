package com.aechak.application.order.support

/**
 * 주문이 적립금 원장에 남기는 멱등키·출처 규칙. 한 자리에 모아 두는 이유는 키가 겹치면 자산이 소실되기 때문이다 —
 * 주문그룹 전체를 되돌리는 복원은 그룹 단위 키를, 품목 일부만 되돌리는 부분취소는 클레임 단위 키를 써야 한다.
 */
object OrderPointLedger {
    const val SOURCE_TYPE = "ORDER_GROUP"

    private const val USE_KEY_PREFIX = "USE:ORDER:"
    private const val RELEASE_KEY_PREFIX = "RELEASE:ORDER:"

    /** 주문그룹 생성 시의 사용 기록 키 */
    fun useKey(orderGroupPublicId: String): String = "$USE_KEY_PREFIX$orderGroupPublicId"

    /** 주문그룹 전체 복원 키 — 그룹당 1행만 허용된다 */
    fun releaseKey(orderGroupPublicId: String): String = "$RELEASE_KEY_PREFIX$orderGroupPublicId"
}
