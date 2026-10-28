package com.aechak.application.inspection.usecase.command

import com.aechak.message.inspection.ContentVerdictMessage

/**
 * 판정 적용 요청.
 *
 * 계약 메시지를 그대로 들고 온다. 이 경계에서 도메인 열거로 옮기는 것이 매핑 지점이고,
 * 계약이 바뀌어도 저장 모델이 따라 흔들리지 않게 막아 주는 자리다.
 */
data class ApplyVerdictCommand(
    val verdict: ContentVerdictMessage,
)
