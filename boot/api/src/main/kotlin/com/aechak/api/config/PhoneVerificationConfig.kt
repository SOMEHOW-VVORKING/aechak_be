package com.aechak.api.config

import com.aechak.application.user.verification.support.VerificationCodeGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.security.SecureRandom
import java.time.Clock

/** 전화 인증 조립 — 코드 생성 전략의 프로파일 분리와 시계 빈. */
@Configuration
class PhoneVerificationConfig {
    /** FE 목업이 고정 코드(000000)를 전제 — 실발송 없는 프로필에서만 유효하다. */
    @Bean
    @Profile("!prod")
    fun fixedVerificationCodeGenerator(): VerificationCodeGenerator = VerificationCodeGenerator { "000000" }

    @Bean
    @Profile("prod")
    fun secureRandomVerificationCodeGenerator(): VerificationCodeGenerator {
        val random = SecureRandom()
        return VerificationCodeGenerator { "%06d".format(random.nextInt(1_000_000)) }
    }

    /** 일 상한의 KST 날짜 버킷 계산용 — 테스트에서 고정 시계로 교체 가능하도록 빈으로 노출한다. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
