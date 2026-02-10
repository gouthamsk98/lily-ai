package com.lilyai.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lilyai.app.ui.navigation.NavGraph
import com.lilyai.app.ui.theme.LilyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        var pendingAuthCode: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthRedirect(intent)
        setContent {
            LilyTheme {
                NavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "lilyai" && uri.host == "callback") {
            val code = uri.getQueryParameter("code")
            Log.i("MainActivity", "OAuth redirect received, code present: ${code != null}")
            if (code != null) {
                pendingAuthCode = code
            }
        }
    }
}
