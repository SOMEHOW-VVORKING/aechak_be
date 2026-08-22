package com.aechak.batch.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * JPA 조립 설정. 엔티티는 domain, Spring Data 인터페이스는 infra:persistence에 있어
 * 메인 클래스 패키지 기준 기본 스캔 범위 밖이므로 스캔 대상을 명시한다.
 * payment는 예외적으로 JPA 엔티티가 domain이 아니라 infra:persistence에 있어 스캔 지정 필요
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.aechak.domain", "com.aechak.infra.persistence.payment")
@EnableJpaRepositories("com.aechak.infra.persistence")
class JpaConfig
