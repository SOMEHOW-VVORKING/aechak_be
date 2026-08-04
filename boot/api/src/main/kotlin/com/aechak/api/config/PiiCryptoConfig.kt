package com.aechak.api.config

import com.aechak.application.pii.PiiCrypto
import com.aechak.infra.persistence.pii.AesKeyRing
import com.aechak.infra.persistence.pii.HmacSupport
import com.aechak.infra.persistence.pii.PiiCryptoAdapter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** PII 암호화 조립 — 키(설정)와 엔진(jpa-persistence)을 묶어 PiiCrypto 포트 빈으로 노출한다. */
@Configuration
@EnableConfigurationProperties(PiiKeyProperties::class)
class PiiCryptoConfig {
    @Bean
    fun piiCrypto(props: PiiKeyProperties): PiiCrypto {
        val decoder = Base64.getDecoder()
        val keys: Map<Byte, SecretKey> =
            props.aesKeys.entries.associate { (version, encoded) ->
                require(version in 1..Byte.MAX_VALUE.toInt()) { "PII AES 키 버전은 1~127 범위여야 합니다: $version" }
                val bytes = decoder.decode(encoded)
                require(bytes.size == AES_KEY_LENGTH) { "PII AES 키(v$version)는 base64 디코드 후 32바이트여야 합니다" }
                version.toByte() to SecretKeySpec(bytes, "AES")
            }
        val hmacKey = decoder.decode(props.hmacKey)
        require(hmacKey.size == HMAC_KEY_LENGTH) { "PII HMAC 키는 base64 디코드 후 32바이트여야 합니다" }
        return PiiCryptoAdapter(
            keyRing = AesKeyRing(keys, props.activeVersion.toByte()),
            hmacSupport = HmacSupport(hmacKey),
        )
    }

    companion object {
        private const val AES_KEY_LENGTH = 32
        private const val HMAC_KEY_LENGTH = 32
    }
}
