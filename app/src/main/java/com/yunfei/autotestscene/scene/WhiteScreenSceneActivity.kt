package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

private const val TAG = "AutoTestSceneApp"

class WhiteScreenSceneActivity : ComponentActivity() {
    private var stopped = false
    private var statusText by mutableStateOf("Running")
    private var showOverlay by mutableStateOf(false)

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersiveMode()
        registerStopReceiver()
        registerBackHandler()
        Log.d(TAG, "WhiteScreenSceneActivity started")

        setContent {
            AutoTestSceneAppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .clickable {
                            showOverlay = !showOverlay
                            Log.d(TAG, "control overlay ${if (showOverlay) "shown" else "hidden"}")
                        }
                ) {
                    if (showOverlay) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Current scene: white screen", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Status: $statusText", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Hint: Back / STOP broadcast to stop", color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { stopScene() }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopScene() {
        Log.d(TAG, "stopScene called")
        if (stopped) return
        stopped = true
        statusText = "Stopped"
        finish()
    }

    override fun onDestroy() {
        unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    private fun registerStopReceiver() {
        registerNotExportedReceiver(stopReceiver, IntentFilter(SceneIds.ACTION_STOP))
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopScene()
            }
        })
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
