package com.aechak.seller.product.support

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** 초안의 중첩 입력을 payload JSON 문자열로 왕복시킨다 */
@Component
class ProductDraftPayloadCodec(
    private val objectMapper: ObjectMapper,
) {
    fun encode(payload: ProductDraftPayload): String = objectMapper.writeValueAsString(payload)

    fun decode(raw: String): ProductDraftPayload = objectMapper.readValue(raw, ProductDraftPayload::class.java)
}
