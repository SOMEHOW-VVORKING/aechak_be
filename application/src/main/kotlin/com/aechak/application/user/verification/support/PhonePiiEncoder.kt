package com.aechak.application.user.verification.support

import com.aechak.application.pii.port.PiiContext
import com.aechak.application.pii.port.PiiCrypto
import org.springframework.stereotype.Component

/** 전화번호 파생값 3종 — 복원용 암호문(저장)과 검색용 해시 2종(점유 조회·어드민 뒷4 검색). */
class PhonePii(
    val encrypted: ByteArray,
    val phoneHmac: ByteArray,
    val last4Hmac: ByteArray,
)

/**
 * 정규화된 전화번호 → 저장 파생값 3종. 컨버터(1필드 1컬럼)로는 다중 컬럼 파생이 불가능해
 * 조회·저장이 같은 값을 쓰는 유스케이스 계층에서 명시 변환한다.
 * 용도 라벨은 PiiContext가 소유한다 — 저장된 전 행과 공유하는 영구 계약이라 한곳에 닫아둔다.
 */
@Component
class PhonePiiEncoder(
    private val piiCrypto: PiiCrypto,
) {
    fun encode(normalizedPhoneNumber: String): PhonePii =
        PhonePii(
            encrypted = piiCrypto.encrypt(normalizedPhoneNumber),
            phoneHmac = piiCrypto.hmac(PiiContext.PHONE, normalizedPhoneNumber),
            last4Hmac = piiCrypto.hmac(PiiContext.PHONE_LAST4, normalizedPhoneNumber.takeLast(4)),
        )
}
