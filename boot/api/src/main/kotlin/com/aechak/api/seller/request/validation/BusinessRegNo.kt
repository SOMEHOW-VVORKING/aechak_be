package com.aechak.api.seller.request.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * 사업자등록번호 형식 검증(10자리·국세청 체크섬) — null은 통과(유형별 필수 여부는 제출 시점 판정).
 * 실재 여부 조회는 하지 않는다.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [BusinessRegNoValidator::class])
annotation class BusinessRegNo(
    val message: String = "사업자등록번호 형식이 올바르지 않습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class BusinessRegNoValidator : ConstraintValidator<BusinessRegNo, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        val digits = value.replace("-", "")
        if (digits.length != 10 || digits.any { !it.isDigit() }) return false
        return checksumOf(digits) == digits.last().digitToInt()
    }

    private fun checksumOf(digits: String): Int {
        val weights = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
        var sum = weights.indices.sumOf { digits[it].digitToInt() * weights[it] }
        sum += digits[8].digitToInt() * 5 / 10
        return (10 - sum % 10) % 10
    }
}
