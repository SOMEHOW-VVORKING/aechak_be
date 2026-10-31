package com.aechak.application.product.draft.usecase

import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import com.aechak.application.product.draft.usecase.command.AutofillProductDraftCommand

/** 폼 초안 받기. 임시저장(ProductDraftUseCase)과 수명주기가 달라 별 유스케이스다 — 저장하지 않는다. */
interface ProductDraftAutofillUseCase {
    fun autofill(command: AutofillProductDraftCommand): ProductDraftAutofillResult
}
