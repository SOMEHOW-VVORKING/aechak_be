package com.aechak.domain.user.user.repository

import com.aechak.domain.user.user.User

interface UserRepository {
    fun findById(id: Long): User?

    fun save(user: User): User
}
