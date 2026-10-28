package com.aechak.seller.config

import com.aechak.infra.kafka.config.MessagingJacksonConfig
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * 검수 요청 발행에 필요한 부품만 가져온다.
 *
 * ``infra.kafka.config`` 패키지를 통째로 스캔하지 않는 이유는 그 안에 컨슈머 오류 처리기가
 * 함께 있기 때문이다. 셀러센터는 메시지를 받지 않으므로 쓰지 않는 빈을 올릴 이유가 없다.
 *
 * 엔벨로프를 만드는 쪽이 이 매퍼를 지목해 주입받으므로, 이 설정이 빠지면
 * 발행 어댑터가 의존을 못 찾아 부팅이 깨진다.
 */
@Configuration(proxyBeanMethods = false)
@Import(MessagingJacksonConfig::class)
class SellerMessagingConfig
