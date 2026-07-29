package com.aechak.domain.user.term.repository

import com.aechak.domain.user.term.Term

interface TermRepository {
    fun findAllActiveOrderedById(): List<Term>
}
