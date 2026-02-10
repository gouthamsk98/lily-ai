package com.lilyai.app.domain.repository

import com.lilyai.app.domain.model.User

interface AuthRepository {
    suspend fun signIn()
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun getAccessToken(): String?
    fun isAuthenticated(): Boolean
}
