package com.aechak.seller.config

import com.aechak.application.user.verification.support.VerificationCodeGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

/** 전화 인증 조립 — 인증 코드 생성 전략과 시계 빈. */
@Configuration
class PhoneVerificationConfig {
    /** 6자리 인증 코드 — 항상 SecureRandom. 통합 테스트는 IntegrationTestConfig가 고정 생성기로 덮는다. */
    @Bean
    fun verificationCodeGenerator(): VerificationCodeGenerator {
        val random = SecureRandom()
        return VerificationCodeGenerator { "%06d".format(random.nextInt(1_000_000)) }
    }

    /** 일 상한의 KST 날짜 버킷 계산용 — 테스트에서 고정 시계로 교체 가능하도록 빈으로 노출한다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
