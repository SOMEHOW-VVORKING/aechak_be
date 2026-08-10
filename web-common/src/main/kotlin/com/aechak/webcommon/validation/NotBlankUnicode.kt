package com.aechak.webcommon.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Kotlin [CharSequence.isBlank] 기준으로 공백 문자열을 거절하는 제약
 * 기본 `@NotBlank`가 허용하는 전각 공백(U+3000)과 NBSP(U+00A0)도 거절하며 null도 무효 처리
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [NotBlankUnicodeValidator::class])
annotation class NotBlankUnicode(
    val message: String = "비어 있을 수 없습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
