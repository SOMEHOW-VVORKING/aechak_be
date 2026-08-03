package com.aechak.application.user.pet.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import java.time.YearMonth

/** 연도만 오면 1월로 채움. 저장 후 '월 모름'과 '1월생'은 구분되지 않음 */
object BirthYearMonthNormalizer {
    private const val YEAR_ONLY_LENGTH = 4

    fun normalize(
        raw: String?,
        today: YearMonth = YearMonth.now(),
    ): String? {
        if (raw == null) return null
        val normalized = if (raw.length == YEAR_ONLY_LENGTH) "$raw-01" else raw
        if (YearMonth.parse(normalized) > today) {
            throw BusinessException(UserErrorCode.INVALID_PET_BIRTH_YEAR_MONTH)
        }
        return normalized
    }
}
