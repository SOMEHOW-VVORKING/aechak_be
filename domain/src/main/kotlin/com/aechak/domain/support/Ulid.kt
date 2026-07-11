package com.aechak.domain.support

import com.github.f4b6a3.ulid.UlidCreator

/**
 * 외부 노출 식별자(public_id) 채번 파사드 — ulid-creator 위임.
 * 26자 Crockford Base32 = 타임스탬프 48bit + 랜덤 80bit. 시간순 정렬 가능(ms 해상도)·열거 노출 방지.
 * 생성 시각(ms)은 앞 10자에서 역산 가능 — 목표가 열거 방지라 시각 노출은 수용한 결정.
 * monotonic 버전을 쓰지 않는 이유: 같은 ms 내 생성 순서가 publicId에 드러나지 않는 쪽이 낫다.
 * 라이브러리 직접 호출을 엔티티에 흩뿌리지 않기 위한 격리 지점 — 교체·모킹은 이 파일만 건드린다.
 */
object Ulid {

    const val LENGTH = 26

    fun generate(): String = UlidCreator.getUlid().toString()
}
