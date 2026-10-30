package com.aechak.application.inquiry.product.usecase.command

data class WithdrawProductInquiryCommand(
    val authorUserId: Long,
    val inquiryPublicId: String,
)
