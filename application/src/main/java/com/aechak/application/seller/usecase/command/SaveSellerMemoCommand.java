package com.aechak.application.seller.usecase.command;

/** 셀러 내부 메모 저장 — 제재 없이 메모만 남기는 경로다. memo가 비면 메모를 지운다. */
public record SaveSellerMemoCommand(long adminId, long sellerId, String memo) {}
