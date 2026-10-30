package com.aechak.application.inquiry.product.usecase.command

data class AnswerProductInquiryCommand(
    val sellerId: Long,
    val inquiryPublicId: String,
    val content: String,
)
