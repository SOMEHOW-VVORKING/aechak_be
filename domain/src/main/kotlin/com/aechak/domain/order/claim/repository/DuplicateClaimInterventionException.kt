package com.aechak.domain.order.claim.repository

/** 같은 클레임에 개입 기록이 이미 저장된 경우 발생한다 — `claim_id` UNIQUE 위반을 옮긴 것이다. */
class DuplicateClaimInterventionException(
    cause: Throwable,
) : RuntimeException(cause)
