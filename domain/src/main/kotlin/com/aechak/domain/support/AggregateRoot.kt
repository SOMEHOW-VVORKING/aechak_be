package com.aechak.domain.support

import jakarta.persistence.Transient

/**
 * 도메인 이벤트 수집 베이스. 애그리거트가 registerEvent()로 이벤트를 모으고,
 * 발행은 application(Facade)이 커밋 전에 수행한 뒤 clearEvents()를 호출한다.
 *
 * domain은 Spring을 모르므로 ApplicationEventPublisher를 직접 쓰지 않는다.
 * spring-data-commons의 AbstractAggregateRoot도 같은 이유로 쓰지 않는다.
 */
abstract class AggregateRoot : BaseEntity() {
    @Transient
    private val _events = mutableListOf<DomainEvent>()
    val events: List<DomainEvent> get() = _events.toList()

    protected fun registerEvent(event: DomainEvent) {
        _events += event
    }

    fun clearEvents() {
        _events.clear()
    }
}
