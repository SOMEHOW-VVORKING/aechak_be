package com.aechak.application.user.term.usecase

import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult

interface ConsentUseCase {
    fun getActiveTerms(): List<TermResult>

    fun submitConsents(command: SubmitConsentsCommand)
}
