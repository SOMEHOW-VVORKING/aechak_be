package com.aechak.application.inquiry.product.support

/**
 * 문의 작성자 표시명. 닉네임 원문을 와이어에 싣지 않는다 —
 * 상품 상세 문의는 아무 구매자나 읽는 자리라서 작성자를 특정할 수 있으면 안 된다.
 */
object AuthorNameMask {
    private const val MASK = "*"
    private const val MIN_MASK_LENGTH = 2
    private const val UNKNOWN = "익명"

    fun of(nickname: String?): String {
        val name = nickname?.trim().orEmpty()
        if (name.isEmpty()) return UNKNOWN
        return name.first() + MASK.repeat(maxOf(name.length - 1, MIN_MASK_LENGTH))
    }
}
