package com.budgettracker.domain.repository

import com.budgettracker.domain.model.User

interface AuthRepository {
    suspend fun signIn()
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun getAccessToken(): String?
    fun isAuthenticated(): Boolean
}
