package com.aechak.application.inspection.port

import com.aechak.application.inspection.port.view.ViolationView

/** 저장된 위반 JSON을 읽음. 모양이 맞지 않으면 예외 대신 null을 돌려줌 */
fun interface ViolationDeserializer {
    fun fromJson(json: String): List<ViolationView>?
}
