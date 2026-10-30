package com.aechak.admin.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 어드민 신고 조회·처리 스캔.
 *
 * <p>application.report 를 통째로 열지 않고 admin 하위만 넣는다 — 같은 패키지의 신고 접수 통지
 * 리스너는 EmailSender 어댑터와 통지 정책 빈을 요구해 어드민이 들 수 없다(문의 알림과 같은 결).
 *
 * <p>이 패키지의 빈이 호출하는 비노출·제재 경로(application.product.product.service,
 * application.review.admin, application.user.sanction)는 이미 다른 스캔이 들여놓고 있어
 * 여기서 더 넣지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.aechak.application.report.admin")
public class AdminReportScanConfig {}
