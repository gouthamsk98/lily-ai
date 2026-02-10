package com.lilyai.app.ui.screens.login

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.MainActivity
import com.lilyai.app.data.remote.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    companion object {
        private const val COGNITO_DOMAIN = "budget-tracker.auth.ap-south-1.amazoncognito.com"
        private const val CLIENT_ID = "42q1k1ra7mofct3r3qeltgq7u1"
        private const val REDIRECT_URI = "lilyai://callback"
    }

    init {
        checkAuth()
    }

    fun checkAuth() {
        viewModelScope.launch {
            // Check if we have a pending auth code from redirect
            val code = MainActivity.pendingAuthCode
            if (code != null) {
                MainActivity.pendingAuthCode = null
                Log.i("LoginViewModel", "Found pending auth code, exchanging...")
                exchangeCodeForTokens(code)
                return@launch
            }
            // Check stored tokens
            val token = TokenStore.getIdToken()
            if (token != null) {
                Log.i("LoginViewModel", "Found stored token, validating...")
                // Validate token by calling API
                try {
                    apiService.getMe() // throws on 401
                    Log.i("LoginViewModel", "Token valid")
                    _state.value = LoginState(isLoading = false, isAuthenticated = true)
                } catch (e: Exception) {
                    Log.w("LoginViewModel", "Token invalid, attempting refresh...")
                    val refreshed = tryRefreshToken()
                    if (refreshed) {
                        _state.value = LoginState(isLoading = false, isAuthenticated = true)
                    } else {
                        Log.i("LoginViewModel", "Refresh failed, clearing tokens")
                        TokenStore.clear()
                        _state.value = LoginState(isLoading = false, isAuthenticated = false)
                    }
                }
            } else {
                Log.i("LoginViewModel", "No stored token")
                _state.value = LoginState(isLoading = false, isAuthenticated = false)
            }
        }
    }

    private suspend fun tryRefreshToken(): Boolean {
        val refreshToken = TokenStore.getRefreshToken() ?: return false
        return try {
            val tokenUrl = "https://$COGNITO_DOMAIN/oauth2/token"
            val client = okhttp3.OkHttpClient()
            val body = okhttp3.FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .build()
            val request = okhttp3.Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build()
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val json = org.json.JSONObject(responseBody)
                val idToken = json.getString("id_token")
                val accessToken = json.getString("access_token")
                // Refresh token is not returned on refresh, keep the old one
                TokenStore.saveTokens(idToken, accessToken, refreshToken)
                Log.i("LoginViewModel", "Token refreshed successfully")
                true
            } else {
                Log.e("LoginViewModel", "Token refresh failed: $responseBody")
                false
            }
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Token refresh error", e)
            false
        }
    }

    private suspend fun exchangeCodeForTokens(code: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val tokenUrl = "https://$COGNITO_DOMAIN/oauth2/token"
            val client = okhttp3.OkHttpClient()
            val body = okhttp3.FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .add("redirect_uri", REDIRECT_URI)
                .build()
            val request = okhttp3.Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build()

            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string()
            Log.i("LoginViewModel", "Token exchange response: ${response.code}")

            if (response.isSuccessful && responseBody != null) {
                val json = org.json.JSONObject(responseBody)
                val idToken = json.getString("id_token")
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                TokenStore.saveTokens(idToken, accessToken, refreshToken)
                Log.i("LoginViewModel", "Tokens saved successfully")
                _state.value = LoginState(isLoading = false, isAuthenticated = true)
            } else {
                Log.e("LoginViewModel", "Token exchange failed: $responseBody")
                _state.value = LoginState(isLoading = false, error = "Login failed. Please try again.")
            }
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Token exchange error", e)
            _state.value = LoginState(isLoading = false, error = "Login failed: ${e.message}")
        }
    }

    fun signIn(activity: Activity) {
        val url = "https://$COGNITO_DOMAIN/oauth2/authorize?" +
            "client_id=$CLIENT_ID&" +
            "response_type=code&" +
            "scope=openid+email+profile&" +
            "redirect_uri=$REDIRECT_URI&" +
            "identity_provider=Google"
        Log.i("LoginViewModel", "Opening Cognito Hosted UI")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(intent)
    }
}
