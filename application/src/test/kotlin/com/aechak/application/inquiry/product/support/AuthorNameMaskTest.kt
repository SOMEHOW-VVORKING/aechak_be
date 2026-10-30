package com.aechak.application.inquiry.product.support

import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약 테스트 — 마스킹이 첫 글자 말고는 아무것도 내보내지 않는다는 규칙을 고정한다. */
class AuthorNameMaskTest {
    @Test
    fun `첫 글자만 남기고 나머지는 고정 길이로 가린다`() {
        assertEquals("김**", AuthorNameMask.of("김하늘"))
        assertEquals("이**", AuthorNameMask.of("이구름"))
    }

    /**
     * 별 개수를 닉네임 길이에 맞추면 "첫 글자 + 글자 수"가 나간다. 같은 상품의 공개 리뷰는
     * 닉네임 원문을 싣기 때문에, 그 둘만으로 비공개 문의 작성자를 후보에서 좁힐 수 있다.
     */
    @Test
    fun `닉네임 길이는 드러나지 않는다`() {
        assertEquals(AuthorNameMask.of("박찬"), AuthorNameMask.of("박찬호선수응원단장"))
        assertEquals("박**", AuthorNameMask.of("박찬호선수응원단장"))
    }

    @Test
    fun `한 글자 닉네임도 길이를 드러내지 않는다`() {
        assertEquals("김**", AuthorNameMask.of("김"))
    }

    @Test
    fun `닉네임이 없거나 공백뿐이면 익명이다`() {
        assertEquals("익명", AuthorNameMask.of(null))
        assertEquals("익명", AuthorNameMask.of("   "))
    }

    @Test
    fun `앞뒤 공백은 첫 글자 판정에 끼지 않는다`() {
        assertEquals("김**", AuthorNameMask.of("  김하늘  "))
    }
}
