package com.aechak.domain.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 파사드 계약 테스트 — 스펙 정합(비트 연산)은 ulid-creator가 보증하므로 형식·유일성만 고정한다. */
class UlidTest {

    @Test
    fun `길이 26·Crockford 알파벳만 사용한다`() {
        repeat(1_000) {
            val ulid = Ulid.generate()
            assertEquals(Ulid.LENGTH, ulid.length)
            assertTrue(ulid.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" }, "위반: $ulid")
        }
    }

    @Test
    fun `대량 생성에도 충돌하지 않는다`() {
        val generated = (1..100_000).map { Ulid.generate() }.toSet()
        assertEquals(100_000, generated.size)
    }

    @Test
    fun `시간이 흐르면 사전순도 증가한다 - 시간순 정렬`() {
        val earlier = Ulid.generate()
        Thread.sleep(2)
        val later = Ulid.generate()
        assertTrue(earlier < later)
    }
}
