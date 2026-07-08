package com.aechak.api.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * JPA 조립 설정. 엔티티는 domain, Spring Data 인터페이스는 infra:persistence에 있어
 * 메인 클래스 패키지 기준 기본 스캔 범위 밖이므로 스캔 대상을 명시한다.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.aechak.domain")
@EnableJpaRepositories("com.aechak.infra.persistence")
class JpaConfig
