package com.aechak.infra.persistence.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * QueryDSL 조립 — JPAQueryFactory를 빈으로 제공해 어댑터가 주입받게 한다.
 * QueryDSL 의존은 이 infra 모듈에 가둔다(domain은 Q클래스 생성용으로만, boot는 무관).
 * boot의 scanBasePackages=["com.aechak"]가 이 설정을 스캔한다.
 */
@Configuration(proxyBeanMethods = false)
class QuerydslConfig {
    @Bean
    fun jpaQueryFactory(entityManager: EntityManager): JPAQueryFactory = JPAQueryFactory(entityManager)
}
