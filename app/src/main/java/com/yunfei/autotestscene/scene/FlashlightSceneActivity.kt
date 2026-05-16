package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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

private const val TAG = "AutoTestSceneApp"

class FlashlightSceneActivity : ComponentActivity() {
    private var stopped = false
    private var statusText by mutableStateOf("Stopped")
    private var torchCameraId by mutableStateOf("N/A")
    private var cameraManager: CameraManager? = null

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
        registerBackHandler()
        startFlashlight()

        setContent {
            AutoTestSceneAppTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "褰撳墠鍦烘櫙锛氭墜鐢电瓛")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "褰撳墠鐘舵€侊細$statusText")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Torch Camera ID: $torchCameraId")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "鍋滄鏂瑰紡锛欱ack 杩斿洖 / STOP 骞挎挱 / 鍋滄鎸夐挳")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { stopScene(shouldFinish = true) }) {
                        Text("鍋滄")
                    }
                }
            }
        }
    }

    private fun startFlashlight() {
        runCatching {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager = manager
            val id = findTorchCameraId(manager)
            if (id == null) {
                statusText = "Error"
                torchCameraId = "N/A"
                Log.e(TAG, "No back torch camera found")
                return
            }
            torchCameraId = id
            manager.setTorchMode(id, true)
            statusText = "Running"
            Log.d(TAG, "flashlight started: cameraId=$id")
        }.onFailure {
            statusText = "Error"
            Log.e(TAG, "Failed to start flashlight", it)
        }
    }

    private fun findTorchCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull { id ->
            runCatching {
                val ch = manager.getCameraCharacteristics(id)
                val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            }.getOrDefault(false)
        }
    }

    private fun stopScene(shouldFinish: Boolean) {
        Log.d(TAG, "flashlight stopScene called")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }
        stopped = true
        statusText = "Stopped"
        runCatching {
            val id = torchCameraId
            if (id.isNotBlank() && id != "N/A") {
                cameraManager?.setTorchMode(id, false)
            }
        }.onFailure {
            Log.e(TAG, "Failed to stop flashlight", it)
        }
        Log.d(TAG, "flashlight stopped")
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

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopScene(shouldFinish = true)
            }
        })
    }
}
