package com.aechak.domain.settlement.account.repository

import com.aechak.domain.settlement.account.SettlementAccount

interface SettlementAccountRepository {
    fun save(account: SettlementAccount): SettlementAccount
}
