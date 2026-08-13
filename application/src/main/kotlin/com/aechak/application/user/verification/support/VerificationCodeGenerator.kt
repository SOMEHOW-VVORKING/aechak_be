package com.aechak.application.user.verification.support

/**
 * 인증 코드 생성 전략 — 비운영은 고정 코드(FE 목업 계약), 운영은 SecureRandom. 프로파일별 조립은 boot 소관.
 */
fun interface VerificationCodeGenerator {
    fun generate(): String
}
