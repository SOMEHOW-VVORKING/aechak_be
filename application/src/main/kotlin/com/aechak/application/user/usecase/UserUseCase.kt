package com.aechak.application.user.usecase

import com.aechak.application.user.usecase.command.RegisterUserCommand
import com.aechak.application.user.usecase.query.UserSearchQuery
import com.aechak.application.user.usecase.result.UserResult
import com.aechak.application.user.usecase.result.UserSummaryResult

/**
 * user 도메인의 유일한 진입점 계약 — 각 도메인이 따라갈 UseCase 템플릿.
 *
 * [진입점 규칙]
 * - 외부(Controller/Consumer/Batch/타 도메인 Facade)는 이 패키지(usecase/) 아래만 import한다.
 *   facade/·service/는 내부 구현 — 타 도메인이 user를 부를 때도 이 인터페이스만, Facade/Service 직접 호출 금지.
 * - 메서드가 10개를 넘어 비대해지면 그때 기능별 분리를 논의한다.
 *
 * [입력 규칙 — 아래 세 메서드가 세 가지 경우의 예시다]
 * - 쓰기: 인자 개수와 무관하게 **항상 Command** → register(RegisterUserCommand)
 *   (요구사항 추가 시 시그니처 대신 필드가 늘어나 호출부 전파가 최소화된다)
 * - 단순 조회(식별자 등 스칼라 1~2개): Query 객체를 만들지 않고 **그냥 인자로** → getUser(userId)
 *   (과설계 금지 — 스칼라 한두 개를 객체로 감싸지 않는다)
 * - 복잡 조회(조건 3개 이상 / 페이징 / 옵셔널 필터): **SearchQuery로 승격** → searchUsers(UserSearchQuery)
 *
 * [출력 규칙]
 * - 항상 Result 계열 — 도메인 엔티티 반환 금지. 상세는 UserResult, 목록/요약은 UserSummaryResult로 분리.
 */
interface UserUseCase {
    fun register(command: RegisterUserCommand): UserResult
    fun getUser(userId: Long): UserResult
    fun searchUsers(query: UserSearchQuery): List<UserSummaryResult>

    /**
     * 소셜 가입 — 프로필(닉네임) 없는 PENDING_ONBOARDING 계정을 만든다(닉네임은 온보딩에서).
     * 프로필이 아직 없어 UserResult(nickname 필수) 대신 식별자만 반환한다.
     */
    fun registerFromSocial(): Long
}
