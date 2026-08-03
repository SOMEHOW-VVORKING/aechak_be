package com.aechak.api.user.pet.request

import com.aechak.domain.user.pet.PetProfile

/**
 * 등록·수정 요청이 공유하는 제약.
 */
object PetProfileConstraints {
    const val NAME_MAX = PetProfile.NAME_MAX

    const val IMAGE_KEY_MAX = 1024

    const val BIRTH_YEAR_MONTH_PATTERN = """^\d{4}(-(0[1-9]|1[0-2]))?$"""

    const val WEIGHT_MIN = PetProfile.WEIGHT_MIN
    const val WEIGHT_MAX = PetProfile.WEIGHT_MAX

    const val WEIGHT_INTEGER_DIGITS = 3
    const val WEIGHT_FRACTION_DIGITS = 1
}
