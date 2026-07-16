package com.aechak.api.user

import com.aechak.api.user.request.RegisterUserRequest
import com.aechak.api.user.response.UserResponse
import com.aechak.application.user.usecase.UserUseCase
import com.aechak.webcommon.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
@RequestMapping("/users") // 접두(api.base-path)는 WebConfig가 일괄 부착
class UserController(
    private val userUseCase: UserUseCase,
) {
    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterUserRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val result = userUseCase.register(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(UserResponse.from(result)))
    }

    @GetMapping("/{userId}")
    fun getUser(
        @PathVariable userId: Long,
    ): ResponseEntity<ApiResponse<UserResponse>> = ResponseEntity.ok(ApiResponse.of(UserResponse.from(userUseCase.getUser(userId))))
}
