package com.aechak.application.user.service

import com.aechak.application.user.usecase.UserUseCase
import com.aechak.application.user.usecase.command.RegisterUserCommand
import com.aechak.application.user.usecase.query.UserSearchQuery
import com.aechak.application.user.usecase.result.UserResult
import com.aechak.application.user.usecase.result.UserSummaryResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.aechak.domain.user.user.User

/**
 * UserUseCase의 유일한 구현체 — 각 도메인이 따라갈 Facade 템플릿.
 *
 * - UseCase 구현은 항상 Facade다. Service가 UseCase를 직접 구현하지 않는다.
 * - @Transactional 경계는 여기 고정 — Service/도메인 메서드에는 붙이지 않는다. 조회는 readOnly = true.
 * - 도메인이 수집한 이벤트(aggregate.events)를 커밋 전 발행하고 clearEvents() 한다.
 * - 타 도메인 협력이 필요하면 그쪽 UseCase를 주입받는다. 순환 의존이 생기면 이벤트 전환을 검토한다.
 */
@Service
class UserFacade(
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher,
) : UserUseCase {

    @Transactional
    override fun register(command: RegisterUserCommand): UserResult {
        // TODO: 외부 지식 검증(닉네임 중복 등) → User.register() → 저장
        //       → aggregate.events 발행 → clearEvents() → Result 변환
        TODO("골격 템플릿 — 기능 구현 시 채운다")
    }

    @Transactional(readOnly = true)
    override fun getUser(userId: Long): UserResult =
        UserResult.from(userService.getById(userId))

    @Transactional(readOnly = true)
    override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> {
        // TODO: 필터·페이징 조회 — 포트에 검색 메서드 추가 시 구현 (복잡 조회 전략은 CQRS-lite 논의)
        TODO("골격 템플릿 — 기능 구현 시 채운다")
    }

    @Transactional
    override fun registerFromSocial(): Long = userService.registerFromSocial().id
}
