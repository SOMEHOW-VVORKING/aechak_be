package com.aechak.application.inspection.port

import com.aechak.message.inspection.Violation

/**
 * 위반 상세를 저장할 JSON 문자열로 바꾼다.
 *
 * 이 계층에 Jackson을 들이지 않으려고 포트로 둔다. 구현은 메시지 직렬화를 이미 소유한
 * 모듈이 든다 — 같은 매퍼를 써야 저장된 모양과 주고받은 모양이 갈라지지 않는다.
 */
fun interface ViolationSerializer {
    /** 빈 목록이면 null을 돌려 컬럼을 비워 둔다 */
    fun toJson(violations: List<Violation>): String?
}
