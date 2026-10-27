package com.aechak.infra.persistence.sns.list

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 계약: 닉네임 검색어를 글자 그대로 부분 일치하는 LIKE 패턴으로 바꾸는지.
 * 깨지면 검색어의 %, _가 와일드카드로 동작하거나 이스케이프 문자가 다음 글자를 삼킨다.
 */
class SnsUserListQueryAdapterTest {
    @Test
    fun `일반 검색어는 앞뒤에 와일드카드만 붙는다`() {
        assertEquals("%코코%", nicknameContainsPattern("코코"), "일반 검색어는 앞뒤에 %만 붙어야 한다")
    }

    @Test
    fun `퍼센트, 밑줄, 백슬래시는 이스케이프한다`() {
        assertEquals("%\\%%", nicknameContainsPattern("%"), "%는 \\%로 이스케이프해야 한다")
        assertEquals("%\\_%", nicknameContainsPattern("_"), "_는 \\_로 이스케이프해야 한다")
        assertEquals("%\\\\%", nicknameContainsPattern("\\"), "\\는 \\\\로 이스케이프해야 한다")
        assertEquals("%a\\\\\\%b\\_c%", nicknameContainsPattern("a\\%b_c"), "섞인 검색어도 글자마다 이스케이프해야 한다")
    }
}
