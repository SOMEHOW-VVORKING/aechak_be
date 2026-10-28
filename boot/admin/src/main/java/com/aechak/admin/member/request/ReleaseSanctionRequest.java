package com.aechak.admin.member.request;

import jakarta.validation.constraints.Size;

/** 제재 해제 요청 — 고정 어휘 사유가 없는 조치라 상세 사유와 메모만 받는다. 둘 다 생략할 수 있다. */
public record ReleaseSanctionRequest(@Size(max = 500) String reasonDetail, @Size(max = 300) String memo) {}
