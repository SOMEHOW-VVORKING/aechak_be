package com.aechak.admin.support;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 게이트 검증용 고정 표적 컨트롤러 — IntegrationTestConfig가 빈으로 조립한다. */
@RestController
public class SecurityProbeController {

    @GetMapping("/admin/security-probe")
    public Map<String, Boolean> probe() {
        return Map.of("ok", true);
    }
}
