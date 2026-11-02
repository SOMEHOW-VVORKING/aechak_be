package com.aechak.domain.review.summary

import com.aechak.domain.user.pet.enums.Species
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [단위] 체급 경계를 고정한다. AI 서버 selection.py와 어긋나면 같은 아이가 두 레포에서 다른 묶음이 된다 */
class PetWeightBandTest {
    @Test
    fun `경계값은 위 구간에 넣는다`() {
        assertEquals(PetWeightBand.DOG_UNDER_5, PetWeightBand.of(Species.DOG, BigDecimal("4.9")))
        assertEquals(PetWeightBand.DOG_5_10, PetWeightBand.of(Species.DOG, BigDecimal("5.0")))
        assertEquals(PetWeightBand.DOG_25_PLUS, PetWeightBand.of(Species.DOG, BigDecimal("25.0")))
        assertEquals(PetWeightBand.CAT_4_6, PetWeightBand.of(Species.CAT, BigDecimal("4.0")))
        assertEquals(PetWeightBand.CAT_6_PLUS, PetWeightBand.of(Species.CAT, BigDecimal("6.0")))
    }

    @Test
    fun `체중을 모르면 묶지 않는다`() {
        assertNull(PetWeightBand.of(Species.DOG, null))
        assertNull(PetWeightBand.of(Species.CAT, BigDecimal.ZERO))
    }
}
