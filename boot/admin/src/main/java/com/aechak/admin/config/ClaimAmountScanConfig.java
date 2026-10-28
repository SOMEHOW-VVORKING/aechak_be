package com.aechak.admin.config;

import com.aechak.application.order.claim.service.ClaimAmountCalculator;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 환불액 계산기 하나만 집어 온다. 어드민 클레임 목록의 "환불 예정액"이 셀러 목록과 같은 숫자여야 하므로
 * 계산 경로를 복제하지 않고 같은 빈을 쓴다.
 *
 * <p>패키지 단위로 스캔하지 않는 이유: 같은 패키지(com.aechak.application.order.claim.service)에
 * 환불 집행·회수 서비스가 함께 살고 그것들이 PG·재고·적립금 포트를 요구해, 어드민에 없는 어댑터를
 * 찾다가 부팅이 깨진다. 계산기는 의존이 없어 단독으로 조립된다.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = "com.aechak.application.order.claim.service",
        useDefaultFilters = false,
        includeFilters =
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ClaimAmountCalculator.class))
public class ClaimAmountScanConfig {}
