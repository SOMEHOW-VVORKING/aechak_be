package com.aechak.application.auth.usecase.result

import com.aechak.common.error.ErrorCode
import com.aechak.domain.user.user.enums.UserStatus

/**
 * 서버 콜백 완료 결과 — 컨트롤러는 이걸 302 리다이렉트로 번역한다.
 * 실패도 (검증된) returnUrl을 들고 돌아온다: 콜백 응답에는 body 수신자가 없어
 * 에러 역시 FE로 리다이렉트해 전달해야 하기 때문(?error=코드).
 */
sealed interface WebLoginCompletion {
    val returnUrl: String

    data class Success(
        override val returnUrl: String,
        /** 컨트롤러가 Set-Cookie로만 내보낸다 — 리다이렉트 URL·body에 싣지 않는 것이 계약. */
        val refreshToken: String,
        val userStatus: UserStatus,
        val isNew: Boolean,
    ) : WebLoginCompletion

    data class Failure(
        override val returnUrl: String,
        /** ?error= 쿼리에는 code만 실린다 — 리다이렉트라 message·status의 수신자가 없다. */
        val errorCode: ErrorCode,
    ) : WebLoginCompletion
}
