package com.aechak.domain.user

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 사용자 애그리거트 루트 — 도메인 엔티티 작성 방식을 보여주는 템플릿.
 * ERD 확정 시 필드·연관을 채운다. 지금은 패턴 표시용 최소 필드만 있다.
 *
 * 엔티티 공통 규칙:
 * - 상태 변경은 의도가 드러나는 메서드로만. setter 노출 금지(protected set).
 * - 자기 상태만으로 판단 가능한 불변식은 엔티티에, 외부 지식(다른 애그리거트·DB 조회)이
 *   필요한 검증은 application에 둔다. 위반 시 BusinessException(도메인 ErrorCode)을 던진다.
 * - data class 금지 — equals/hashCode가 필요하면 id 기반으로 직접 정의한다.
 * - 다른 도메인이 알아야 할 상태 변경은 registerEvent()로 이벤트를 수집한다.
 */
@Entity
@Table(name = "users")
class User protected constructor(
    nickname: String,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var nickname: String = nickname
        protected set

    companion object {
        /** 생성 시점 불변식은 팩토리에서 강제한다. */
        fun register(nickname: String): User {
            // TODO: 닉네임 형식 등 자기 상태 불변식 검증 + UserRegisteredEvent 수집
            return User(nickname)
        }
    }
}
