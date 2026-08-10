package com.aechak.api.config

import com.aechak.application.pii.port.PiiCrypto
import com.aechak.infra.persistence.pii.AesKeyRing
import com.aechak.infra.persistence.pii.HmacSupport
import com.aechak.infra.persistence.pii.PiiCryptoAdapter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/** PII 암호화 조립 — 키(설정)와 엔진(jpa-persistence)을 묶어 PiiCrypto 포트 빈으로 노출한다. */
@Configuration
@EnableConfigurationProperties(PiiKeyProperties::class)
class PiiCryptoConfig {
    @Bean
    fun piiCrypto(props: PiiKeyProperties): PiiCrypto {
        val keys =
            props.aesKeys.entries.associate { (version, encoded) ->
                keyVersion(version) to SecretKeySpec(decodeKey(encoded, "AES 키(v$version)"), "AES")
            }
        return PiiCryptoAdapter(
            keyRing = AesKeyRing(keys, keyVersion(props.activeVersion)),
            hmacSupport = HmacSupport(decodeKey(props.hmacKey, "HMAC 키")),
        )
    }

    /** 키 버전 1~127 검증 후 Byte로. */
    private fun keyVersion(version: Int): Byte {
        require(version in 1..Byte.MAX_VALUE.toInt()) { "PII AES 키 버전은 1~127 범위여야 합니다: $version" }
        return version.toByte()
    }

    /** base64 디코드 후 32바이트(AES-256·HMAC-SHA256 키 길이) 보장. */
    private fun decodeKey(
        encoded: String,
        label: String,
    ): ByteArray {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size == KEY_LENGTH) { "PII ${label}는 base64 디코드 후 ${KEY_LENGTH}바이트여야 합니다" }
        return bytes
    }

    companion object {
        private const val KEY_LENGTH = 32
    }
}
