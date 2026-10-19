package com.aechak.application.settlement.service

import com.aechak.application.settlement.usecase.command.RegisterVerifiedAccountCommand
import com.aechak.domain.settlement.account.SettlementAccount
import com.aechak.domain.settlement.account.repository.SettlementAccountRepository
import org.springframework.stereotype.Service

/** 정산계좌 비즈니스 로직 보관함 — Facade에서만 호출된다. */
@Service
class SettlementAccountService(
    private val settlementAccountRepository: SettlementAccountRepository,
) {
    /** 셀러당 계좌 1행(UNIQUE seller_id) — 중복 등록은 커밋 시점 제약 위반으로 막히고 호출자가 번역한다. */
    fun registerVerified(command: RegisterVerifiedAccountCommand): SettlementAccount =
        settlementAccountRepository.save(
            SettlementAccount.registerVerified(
                sellerId = command.sellerId,
                bankCode = command.bankCode,
                accountNumberEnc = command.accountNumberEnc,
                accountHolderName = command.accountHolderName,
            ),
        )
}
