package com.aechak.api.user.user

import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.user.user.request.NicknameRequest
import com.aechak.api.user.user.request.SubmitConsentsRequest
import com.aechak.api.user.user.request.UpdateProfileRequest
import com.aechak.api.user.user.response.NicknameCheckResponse
import com.aechak.api.user.user.response.UserMeResponse
import com.aechak.api.user.user.response.WithdrawalCheckResponse
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * user 도메인 API — 각 도메인이 따라갈 Controller 템플릿.
 *
 * - 주입은 UseCase 인터페이스만. Facade/Service/Repository 타입 주입 금지.
 * - 하는 일은 세 가지뿐: ①형식 검증(@Valid) ②Request→Command 변환 ③Result→Response 변환.
 *   비즈니스 판단이 등장하면 application으로 내린다.
 * - 성공 응답은 ApiResponse로 감싸고, 반환할 데이터 없는 성공은 빈 본문 + 상태코드로만 표현한다.
 */
@RestController
@RequestMapping("/users")
class UserController(
    private val userUseCase: UserUseCase,
    private val consentUseCase: ConsentUseCase,
    private val withdrawalUseCase: WithdrawalUseCase,
    private val refreshCookieFactory: RefreshCookieFactory,
) {
    /** 닉네임 사용 가능 검사 — UX 보조(예약 아님, 최종 판정은 PUT의 DB UNIQUE). 본인 현재 닉네임은 available=true. */
    @GetMapping("/nickname/check")
    fun checkNickname(
        @RequestParam nickname: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<NicknameCheckResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(NicknameCheckResponse(available = userUseCase.checkNickname(principal.userId, nickname))),
        )

    /** 닉네임 설정(온보딩 전용) — 온보딩 완료 전이 트리거, ACTIVE는 409. 응답은 변경 후 내 정보(FE는 status로 홈 라우팅). */
    @PutMapping("/me/nickname")
    fun setNickname(
        @RequestBody request: NicknameRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UserMeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(UserMeResponse.from(userUseCase.setNickname(request.toCommand(principal.userId)))),
        )

    /** 내 정보 — 마이 허브·온보딩 재시작 라우팅·프로필 수정 초기값. */
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UserMeResponse>> =
        ResponseEntity.ok(ApiResponse.of(UserMeResponse.from(userUseCase.getMe(principal.userId))))

    /** 프로필 수정(닉네임·자기소개·이미지) — 전체 교체. 온보딩 완료 전이는 PUT /me/nickname 소관. */
    @PutMapping("/me/profile")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<UserMeResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(UserMeResponse.from(userUseCase.updateProfile(request.toCommand(principal.userId)))),
        )

    /** 약관 동의 제출 — 온보딩 전용(PENDING). */
    @PostMapping("/me/consents")
    fun submitConsents(
        @Valid @RequestBody request: SubmitConsentsRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        consentUseCase.submitConsents(request.toCommand(principal.userId))
        return ResponseEntity.noContent().build()
    }

    /** 탈퇴 가능 여부 조회 */
    @GetMapping("/me/withdrawal/check")
    fun checkWithdrawal(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<WithdrawalCheckResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(WithdrawalCheckResponse.from(withdrawalUseCase.checkWithdrawal(principal.userId))),
        )

    /** 회원 탈퇴 */
    @DeleteMapping("/me")
    fun withdraw(
        @AuthenticationPrincipal principal: AuthPrincipal,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        withdrawalUseCase.withdraw(principal.userId)
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.expire(request).toString()) // 쿠키를 만료 시켜 refresh 쿠키 삭제
            .build()
    }
}
