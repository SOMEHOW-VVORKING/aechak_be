package com.aechak.domain.settlement.account

import com.aechak.domain.settlement.account.enums.AccountVerificationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 계약 — 승인 트랜잭션이 만드는 정산계좌의 검증 상태. 깨지면 승인 계좌가 미검증으로 남는다.
 */
class SettlementAccountTest {
    @Test
    fun `registerVerified로 만든 계좌는 VERIFIED 상태다`() {
        val account = SettlementAccount.registerVerified(1L, "004", "enc:abc", "김운경")

        assertEquals(AccountVerificationStatus.VERIFIED, account.verificationStatus, "승인 트랜잭션이 만드는 계좌는 검증 완료 상태여야 한다")
    }
}
