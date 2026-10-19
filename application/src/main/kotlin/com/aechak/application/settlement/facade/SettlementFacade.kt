package com.aechak.application.settlement.facade

import com.aechak.application.settlement.service.SettlementAccountService
import com.aechak.application.settlement.usecase.SettlementUseCase
import com.aechak.application.settlement.usecase.command.RegisterVerifiedAccountCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** SettlementUseCase의 유일한 구현체. @Transactional 경계는 여기 고정. */
@Service
class SettlementFacade(
    private val settlementAccountService: SettlementAccountService,
) : SettlementUseCase {
    /** REQUIRED 전파 — 승인 트랜잭션 안에서 불리면 참여하고, 단독 호출이면 스스로 경계를 연다. */
    @Transactional
    override fun registerVerifiedAccount(command: RegisterVerifiedAccountCommand) {
        settlementAccountService.registerVerified(command)
    }
}
