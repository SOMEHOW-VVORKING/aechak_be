package com.aechak.application.product.draft.port

/**
 * 셀러 폼 초안 생성 포트. 구현은 infra/client/ai-client다.
 *
 * 외부 실패를 예외로 던지지 않고 **결과로 돌린다** — 초안 실패가 셀러의 등록을 막아서는 안 되고,
 * 셀러 잘못이 아닌 실패에 5xx를 주면 FE 전역 에러 토스트가 등록 자체가 깨진 것처럼 보이게 한다.
 * 어댑터가 AI의 403·409·503·422·타임아웃을 전부 UNAVAILABLE로 옮긴다.
 */
interface ProductDraftAutofillPort {
    fun generate(request: ProductDraftAutofillRequest): ProductDraftAutofillResult
}

/**
 * 셀러센터 -> AI.
 *
 * 이미지를 보내지 않는다 — AI 기본 이미지 모드가 OFF라 보낸 base64를 읽지 않고, 그 서버에는
 * S3 접근 코드도 권한도 없다. 자리를 미리 뚫지 않는다.
 *
 * sellerId도 보내지 않는다 — AI 전수에서 읽는 곳이 0건이고(선언과 주석뿐) 캐시 키에도 없다.
 * 셀러-요청 상관은 우리 로그의 requestId+sellerId 한 줄로 이미 된다.
 */
data class ProductDraftAutofillRequest(
    val requestId: String,
    val productName: String,
    val categoryId: Long?,
    val lockedFields: LockedFields?,
) {
    /** 재생성에서 셀러가 고쳐 지켜야 하는 값. 필드 이름은 AI 것을 그대로 쓴다. */
    data class LockedFields(
        val description: String?,
        val optionGroups: List<OptionGroup>?,
    )

    data class OptionGroup(
        val name: String,
        val values: List<String>,
    )
}
