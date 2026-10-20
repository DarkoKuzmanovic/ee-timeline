package com.eetimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.eetimeline.ui.EEApp
import com.eetimeline.ui.theme.EETimelineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EETimelineTheme {
                EEApp(appGraph = (application as EEApplication).appGraph)
            }
        }
    }
}
