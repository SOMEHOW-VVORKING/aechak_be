package com.aechak.infra.persistence.pii

import com.aechak.application.pii.port.PiiContext
import com.aechak.application.pii.port.PiiCrypto

/** PiiCrypto 포트 구현 — 엔진(AesKeyRing·HmacSupport)에 위임만 한다. 빈 조립은 boot 소관. */
class PiiCryptoAdapter(
    private val keyRing: AesKeyRing,
    private val hmacSupport: HmacSupport,
) : PiiCrypto {
    override fun encrypt(plain: String): ByteArray = keyRing.encrypt(plain.toByteArray(Charsets.UTF_8))

    override fun decrypt(cipher: ByteArray): String = String(keyRing.decrypt(cipher), Charsets.UTF_8)

    override fun hmac(
        context: PiiContext,
        value: String,
    ): ByteArray = hmacSupport.hmac(context, value)
}
