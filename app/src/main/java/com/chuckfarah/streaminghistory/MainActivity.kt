package com.chuckfarah.streaminghistory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chuckfarah.streaminghistory.ui.navigation.AppNavGraph
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamingHistoryTheme {
                AppNavGraph()
            }
        }
    }
}
