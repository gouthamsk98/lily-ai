package com.lilyai.app.data.repository

import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import com.lilyai.app.data.remote.ApiService
import com.lilyai.app.domain.model.User
import com.lilyai.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : AuthRepository {

    override suspend fun signIn() {
        // Sign-in is initiated from the ViewModel/Activity with the Activity reference
        // This is a placeholder; actual call happens via Amplify.Auth.signInWithSocialWebUI in LoginViewModel
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
            val cognitoSession = session as? AWSCognitoAuthSession
            cognitoSession?.userPoolTokensResult?.value?.idToken
        } catch (e: Exception) {
            null
        }
    }

    override fun isAuthenticated(): Boolean {
        return try {
            true
        } catch (e: Exception) {
            false
        }
    }
}
