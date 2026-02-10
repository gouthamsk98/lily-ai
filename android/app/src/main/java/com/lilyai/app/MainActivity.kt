package com.lilyai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lilyai.app.ui.navigation.NavGraph
import com.lilyai.app.ui.theme.LilyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LilyTheme {
                NavGraph()
            }
        }
    }
}
