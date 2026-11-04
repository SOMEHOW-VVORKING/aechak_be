package com.aechak.application.product.product.admin.usecase.command;

/** @param reason 막는 사유. 운영 기록이고 셀러에게 보내는 문장이 아니다 */
public record SuspendProductSaleCommand(long adminId, String productPublicId, String reason) {}
