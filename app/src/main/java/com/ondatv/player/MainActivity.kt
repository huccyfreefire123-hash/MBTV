package com.ondatv.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ondatv.player.ui.App
import com.ondatv.player.ui.OndaTheme
import com.ondatv.player.ui.UpdatePrompt

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones browse in portrait; coming back from the (landscape) player restores it by itself.
        if (isPhone()) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            OndaTheme {
                App(vm) { request -> startActivity(PlayerActivity.intent(this, request)) }
                // Offers a newer version when one is published (no store involved).
                UpdatePrompt()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadProgress()
    }
}
