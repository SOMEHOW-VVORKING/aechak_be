package com.aechak.application.order.support

/**
 * 주문이 적립금 원장에 남기는 멱등키·출처 규칙. 한 자리에 모아 두는 이유는 키가 겹치면 자산이 소실되기 때문이다 —
 * 주문그룹 전체를 되돌리는 복원은 그룹 단위 키를, 품목 일부만 되돌리는 부분취소는 클레임 단위 키를 써야 한다.
 */
object OrderPointLedger {
    const val SOURCE_TYPE = "ORDER_GROUP"

    /** 부분취소 복원의 출처 — 되돌린 단위가 그룹이 아니라 클레임이라 구분한다 */
    const val CLAIM_SOURCE_TYPE = "ORDER_CLAIM"

    private const val USE_KEY_PREFIX = "USE:ORDER:"
    private const val RELEASE_KEY_PREFIX = "RELEASE:ORDER:"
    private const val CLAIM_RELEASE_KEY_PREFIX = "RELEASE:CLAIM:"

    /** 주문그룹 생성 시의 사용 기록 키 */
    fun useKey(orderGroupPublicId: String): String = "$USE_KEY_PREFIX$orderGroupPublicId"

    /** 주문그룹 전체 복원 키 — 그룹당 1행만 허용된다 */
    fun releaseKey(orderGroupPublicId: String): String = "$RELEASE_KEY_PREFIX$orderGroupPublicId"

    /**
     * 클레임 환불 복원 키 — 클레임당 1행만 허용된다.
     * 그룹 키를 쓰면 같은 그룹의 두 번째 부분취소가 멱등키 UNIQUE에 걸려 조용히 건너뛰어지고 구매자 자산이 소실된다.
     */
    fun claimReleaseKey(claimPublicId: String): String = "$CLAIM_RELEASE_KEY_PREFIX$claimPublicId"
}
