package com.aechak.infra.persistence.support

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

/** 이름이 constraintName인 UNIQUE 제약 위반이면 true. 제약 이름 앞에 붙는 테이블 이름은 떼고 비교한다 */
internal fun DataIntegrityViolationException.isUniqueViolationOf(constraintName: String): Boolean {
    val violation =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .firstOrNull()
    return violation?.kind == ConstraintViolationException.ConstraintKind.UNIQUE &&
        violation.constraintName?.substringAfterLast('.') == constraintName
}
