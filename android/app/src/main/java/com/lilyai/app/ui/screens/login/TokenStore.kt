package com.lilyai.app.ui.screens.login

import android.content.Context
import android.content.SharedPreferences

object TokenStore {
    private const val PREFS_NAME = "lily_ai_auth"
    private const val KEY_ID_TOKEN = "id_token"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTokens(idToken: String, accessToken: String, refreshToken: String?) {
        prefs?.edit()
            ?.putString(KEY_ID_TOKEN, idToken)
            ?.putString(KEY_ACCESS_TOKEN, accessToken)
            ?.putString(KEY_REFRESH_TOKEN, refreshToken)
            ?.apply()
    }

    fun getIdToken(): String? = prefs?.getString(KEY_ID_TOKEN, null)

    fun getAccessToken(): String? = prefs?.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs?.getString(KEY_REFRESH_TOKEN, null)

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
