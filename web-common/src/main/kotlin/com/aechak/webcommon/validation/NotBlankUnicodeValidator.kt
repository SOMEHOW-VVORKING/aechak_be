package com.aechak.webcommon.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

/** [NotBlankUnicode] 검증기. null이거나 Kotlin [CharSequence.isBlank] 기준 공백만 있으면 무효 처리 */
class NotBlankUnicodeValidator : ConstraintValidator<NotBlankUnicode, CharSequence> {
    override fun isValid(
        value: CharSequence?,
        context: ConstraintValidatorContext,
    ): Boolean = !value.isNullOrBlank()
}
