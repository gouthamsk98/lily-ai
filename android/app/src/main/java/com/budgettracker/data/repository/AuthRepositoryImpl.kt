package com.budgettracker.data.repository

import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.options.AuthSignInOptions
import com.amplifyframework.kotlin.core.Amplify
import com.budgettracker.data.remote.ApiService
import com.budgettracker.domain.model.User
import com.budgettracker.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : AuthRepository {

    override suspend fun signIn() {
        Amplify.Auth.signInWithSocialWebUI(
            com.amplifyframework.auth.AuthProvider.google(),
            // Activity reference handled in ViewModel
        )
    }

    override suspend fun signOut() {
        Amplify.Auth.signOut()
    }

    override suspend fun getCurrentUser(): User? {
        return try {
            apiService.getMe().toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAccessToken(): String? {
        return try {
            val session = Amplify.Auth.fetchAuthSession()
            // Extract ID token from Cognito session
            val cognitoSession = session as? com.amplifyframework.auth.cognito.AWSCognitoAuthSession
            cognitoSession?.userPoolTokensResult?.value?.idToken
        } catch (e: Exception) {
            null
        }
    }

    override fun isAuthenticated(): Boolean {
        return try {
            // This is a simplified check
            true
        } catch (e: Exception) {
            false
        }
    }
}
