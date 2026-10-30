package com.aechak.application.inquiry.product.support

/**
 * 문의 작성자 표시명. 닉네임 원문을 와이어에 싣지 않는다 —
 * 상품 상세 문의는 아무 구매자나 읽는 자리라서 작성자를 특정할 수 있으면 안 된다.
 *
 * 별 개수를 닉네임 길이에 맞추지 않고 **고정한다.** 길이에 맞추면 "첫 글자 + 글자 수"가 나가고,
 * 후보 닉네임을 아는 관찰자(같은 상품의 공개 리뷰는 닉네임 원문을 싣는다)는 그 둘만으로
 * 비공개 문의 작성자를 좁힐 수 있다. 첫 글자를 남기는 것은 같은 상품에 여러 문의가 달렸을 때
 * 같은 사람인지 가늠하라고 둔 것이고, 길이까지 줄 이유는 없다.
 */
object AuthorNameMask {
    private const val MASK = "**"
    private const val UNKNOWN = "익명"

    fun of(nickname: String?): String {
        val name = nickname?.trim().orEmpty()
        if (name.isEmpty()) return UNKNOWN
        return name.first() + MASK
    }
}
