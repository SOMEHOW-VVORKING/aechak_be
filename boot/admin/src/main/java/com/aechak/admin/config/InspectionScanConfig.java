package com.aechak.admin.config;

import com.aechak.application.inspection.facade.InspectionFacade;
import com.aechak.application.inspection.service.InspectionRequestService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 검수 패키지 스캔. 판정 적용(api)과 요청 발행(셀러센터)은 카프카 배선을 요구해 뺌.
 * 같은 패키지에 어드민 큐 파사드가 함께 살아 패키지 단위 스캔으로는 가릴 수 없기 때문임.
 */
@Configuration
@ComponentScan(
        basePackages = "com.aechak.application.inspection",
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {InspectionFacade.class, InspectionRequestService.class}))
public class InspectionScanConfig {}
