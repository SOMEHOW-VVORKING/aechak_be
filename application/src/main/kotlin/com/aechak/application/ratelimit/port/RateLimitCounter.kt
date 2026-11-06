package com.aechak.application.ratelimit.port

import com.aechak.application.ratelimit.usecase.RateLimit

/** 빈도 제한 카운터 저장소(Redis) */
interface RateLimitCounter {
    /**
     * 키마다 한도 아래면 모든 키를 1씩 올리고 true다. 하나라도 찼으면 아무것도 올리지 않고 false다.
     * 키는 처음 올릴 때 그 창 길이만큼 뒤에 만료된다.
     */
    fun tryAcquire(counters: Map<String, RateLimit>): Boolean
}
