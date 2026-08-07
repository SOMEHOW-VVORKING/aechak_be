package com.aechak.application.user.verification.support

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
 * context 라벨은 저장된 전 행과 공유하는 영구 계약 — 변경은 전량 재계산 이벤트로만 가능하다.
 */
@Component
class PhonePiiEncoder(
    private val piiCrypto: PiiCrypto,
) {
    fun encode(normalizedPhoneNumber: String): PhonePii =
        PhonePii(
            encrypted = piiCrypto.encrypt(normalizedPhoneNumber),
            phoneHmac = piiCrypto.hmac(CONTEXT_PHONE, normalizedPhoneNumber),
            last4Hmac = piiCrypto.hmac(CONTEXT_PHONE_LAST4, normalizedPhoneNumber.takeLast(4)),
        )

    companion object {
        const val CONTEXT_PHONE = "phone"
        const val CONTEXT_PHONE_LAST4 = "phone-last4"
    }
}
