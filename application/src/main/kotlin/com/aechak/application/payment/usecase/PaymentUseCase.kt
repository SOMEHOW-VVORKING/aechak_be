package com.aechak.application.payment.usecase

import com.aechak.application.payment.usecase.command.ApplyPaymentCancelCommand
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByGatewayCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByWebhookCommand
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CancelPaymentResult
import com.aechak.application.payment.usecase.result.CompletePaymentResult
import com.aechak.application.payment.usecase.result.GatewayCancelResult
import com.aechak.application.payment.usecase.result.PreparePaymentResult

interface PaymentUseCase {
    /** 결제 행을 만들고 포트원에 금액을 사전등록함. 재호출도 같은 행으로 사전등록을 다시 태움 */
    fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult

    /**
     * 결제창이 닫힌 뒤의 확정 — 포트원에 진실을 물어 승인이면 주문그룹을 선점 전이하고, 실패면 기록한다.
     * 미완료(미시도·승인 대기)는 전이 없이 상태만 돌려준다. 재호출은 멱등.
     */
    fun completePayment(command: CompletePaymentCommand): CompletePaymentResult

    /**
     * 게이트웨이가 보낸 웹훅으로 하는 확정 — 콜백과 같은 흐름을 타되 신원은 로그인이 아니라 서명이 보증한다.
     * 확정과 무관한 사건이거나 우리가 모르는 결제면 null.
     */
    fun completePaymentByWebhook(command: CompletePaymentByWebhookCommand): CompletePaymentResult?

    /**
     * 결제 식별자만으로 하는 확정 — 대사·만료 배치처럼 서버 안에서 부르는 입구. 웹훅과 같은 흐름이고 서명 검증만 없다.
     * 우리가 모르는 결제면 null. 결과 status가 PAID일 때만 확정이 성립한 것이다(그 외는 미성사 상태 보고).
     */
    fun completePaymentByGateway(command: CompletePaymentByGatewayCommand): CompletePaymentResult?

    /**
     * 승인된 결제의 전액·부분 취소. 취소 선점을 먼저 커밋해 동시 취소를 하나로 좁히고,
     * PG 호출은 트랜잭션 밖에서 하고, PG가 확정한 금액만 원장(취소가능액)에서 뺀다.
     * 원승인액은 줄지 않는다. PG가 비동기로 접수만 하면 원장을 건드리지 않고 요청 상태로 남긴다.
     */
    fun cancelPayment(command: CancelPaymentCommand): CancelPaymentResult

    /**
     * 취소의 앞 절반 — 취소 선점을 독립 커밋하고(호출자 트랜잭션에 합류하지 않는다) PG 취소를 트랜잭션 밖에서 호출한다.
     * 원장은 건드리지 않으므로 확정 금액은 호출자가 applyCancelledAmount 로 반영해야 한다.
     * 원장 차감을 호출자의 다른 쓰기와 한 트랜잭션에 묶어야 하는 흐름(클레임 환불 집행)이 쓴다.
     * PG가 거절하거나 호출이 터지면 선점을 풀고 예외를 던진다.
     */
    fun requestCancelAtGateway(command: CancelPaymentCommand): GatewayCancelResult

    /**
     * 취소의 뒤 절반 — PG가 확정한 금액만 취소가능액에서 뺀다. 원승인액은 줄지 않는다.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 주문그룹 전이와 같은 트랜잭션이어야 대사가 둘을 미동기화로 보지 않는다.
     */
    fun applyCancelledAmount(command: ApplyPaymentCancelCommand): CancelPaymentResult
}
