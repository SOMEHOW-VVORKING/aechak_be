package com.aechak.pii

import org.springframework.security.crypto.encrypt.AesBytesEncryptor
import org.springframework.security.crypto.encrypt.AesBytesEncryptor.CipherAlgorithm
import org.springframework.security.crypto.keygen.KeyGenerators
import javax.crypto.SecretKey

/**
 * 버전 키링 AES-GCM 엔진. 암호문 프레임 = [키 버전 1바이트][IV·암호문·GCM 태그].
 * 버전 바이트는 키 로테이션 훅 — 새 키를 추가해도 옛 버전으로 잠긴 행을 계속 복호할 수 있다.
 * 같은 평문도 매번 다른 암호문이 나온다(랜덤 IV) — 동등 비교·검색은 HMAC 컬럼이 담당한다.
 */
class AesKeyRing(
    keys: Map<Byte, SecretKey>,
    private val activeVersion: Byte,
) {
    private val encryptors: Map<Byte, AesBytesEncryptor> =
        keys.mapValues { (_, key) -> AesBytesEncryptor(key, KeyGenerators.secureRandom(GCM_IV_LENGTH), CipherAlgorithm.GCM) }

    init {
        require(encryptors.containsKey(activeVersion)) { "활성 버전($activeVersion)의 키가 등록돼 있지 않습니다" }
    }

    fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(activeVersion) + encryptors.getValue(activeVersion).encrypt(plain)

    fun decrypt(framed: ByteArray): ByteArray {
        require(framed.size > 1) { "PII 암호문 프레임이 비어 있습니다" }
        val version = framed[0]
        val encryptor =
            encryptors[version]
                ?: throw IllegalStateException("미등록 PII 키 버전입니다: $version — 키링 설정과 데이터가 어긋났다")
        return encryptor.decrypt(framed.copyOfRange(1, framed.size))
    }

    companion object {
        private const val GCM_IV_LENGTH = 12
    }
}
