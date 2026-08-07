package com.aechak.application.seller.usecase

import com.aechak.application.seller.usecase.command.SaveDraftCommand
import com.aechak.application.seller.usecase.result.ApplicationResult

/**
 * 입점 신청(신청자 측) 진입점 계약. 셀러(스토어) 운영 쪽은 SellerUseCase가 따로 맡는다.
 * 유저당 신청서 1건(행 재사용 모델)이라 신청자 측 경로에 applicationId가 없다.
 * 입출력 규칙은 user 템플릿(UserUseCase) 참조.
 */
interface SellerApplicationUseCase {
    /**
     * 신청 저장(임시저장) — 폼+서류 일괄. 없으면 DRAFT 생성, DRAFT면 수정, REJECTED면 재작성 진입(reopen) 후 수정.
     * 서류는 부분 upsert(보낸 종류만 등록/교체, 미포함 종류 유지) — 승격 검증은 file 도메인(100002·100003).
     * 전제: 휴대폰 인증(10103)·비셀러(10001). SUBMITTED 수정은 10101, 동시 최초 생성은 10104.
     */
    fun saveDraft(command: SaveDraftCommand): ApplicationResult

    /** 내 신청 현황 — 없으면 10100. */
    fun getMe(userId: Long): ApplicationResult

    /**
     * 신청 제출 — 유형별 필수 정보·서류 세트 검증(미비 10105, 부족 목록 동봉) 후 SUBMITTED로 전환.
     * 내 신청서가 대상이라 id 불요 — 없으면 10100, DRAFT 아니면 10101.
     */
    fun submit(userId: Long)
}
