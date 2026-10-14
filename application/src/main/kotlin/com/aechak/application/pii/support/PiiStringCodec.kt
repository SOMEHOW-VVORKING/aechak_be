package com.aechak.application.pii.support

import com.aechak.application.pii.port.PiiCrypto
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * PII 문자열의 저장 표현 변환 — AES 암호문(ByteArray)을 Base64 문자열로 왕복한다.
 * 암호문을 String 컬럼에 담는 소비자(주문 배송지 스냅샷·셀러 계좌번호)의 공용 부품.
 */
@Component
class PiiStringCodec(
    private val piiCrypto: PiiCrypto,
) {
    fun encrypt(plain: String): String = Base64.getEncoder().encodeToString(piiCrypto.encrypt(plain))

    fun decrypt(encoded: String): String = piiCrypto.decrypt(Base64.getDecoder().decode(encoded))
}
