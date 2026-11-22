package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

class MotorSceneActivity : ControlledSceneActivity() {
    private var vibrator: Vibrator? = null
    private var isStopped = false

    private var statusText by mutableStateOf("Running")

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene(shouldFinish = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerStopReceiver()

        startVibration()

        setContent {
            AutoTestSceneAppTheme {
                SceneScreen(
                    sceneName = "椹揪闇囧姩",
                    statusText = statusText,
                    onStopClick = { stopScene(shouldFinish = true) }
                )
            }
        }
    }

    private fun startVibration() {
        val vib = getSystemService(Vibrator::class.java)
        if (vib?.hasVibrator() != true) {
            statusText = "Vibrator unavailable"
            resourceError("VIBRATOR_UNAVAILABLE")
            return
        }
        vibrator = vib
        runCatching {
            vib.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
            )
            resourceReady()
        }.onFailure {
            statusText = "Vibration failed"
            resourceError("VIBRATION_START_FAILED")
        }
    }

    private fun stopScene(shouldFinish: Boolean) {
        if (isStopped) {
            if (shouldFinish) finish()
            return
        }
        isStopped = true
        statusText = "Stopped"
        runCatching { vibrator?.cancel() }.onFailure { resourceError("VIBRATION_STOP_FAILED") }
        vibrator = null
        if (shouldFinish) finish()
    }

    override fun onDestroy() {
        stopScene(shouldFinish = false)
        unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    private fun registerStopReceiver() {
        registerNotExportedReceiver(stopReceiver, IntentFilter(SceneIds.ACTION_STOP))
    }
}

@androidx.compose.runtime.Composable
private fun SceneScreen(
    sceneName: String,
    statusText: String,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "马达")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "状态：${operatorStatus(statusText)}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "按返回键退出")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopClick) {
            Text("停止场景")
        }
    }
}
