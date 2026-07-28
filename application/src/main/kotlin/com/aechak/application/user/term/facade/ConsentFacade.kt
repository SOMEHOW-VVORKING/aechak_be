package com.aechak.application.user.term.facade

import com.aechak.application.user.term.service.ConsentService
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.application.user.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConsentFacade(
    private val consentService: ConsentService,
    private val userService: UserService,
) : ConsentUseCase {
    @Transactional(readOnly = true)
    override fun getActiveTerms(): List<TermResult> = consentService.getActiveTerms().map(TermResult::from)

    @Transactional
    override fun submitConsents(command: SubmitConsentsCommand) {
        val user = userService.getById(command.userId)
        consentService.submit(user, command.items)
    }
}
