package com.yunfei.autotestscene.scene

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

abstract class BaseCameraSceneActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var isStopped = false
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var statusText by mutableStateOf("Running")

    protected abstract val sceneTitle: String
    protected abstract val lensFacing: Int
    protected open val showOverlayInfo: Boolean = true

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene(shouldFinish = true)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraIfReady()
        } else {
            statusText = "Stopped (CAMERA permission denied)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersiveMode()
        registerStopReceiver()
        registerBackHandler()

        setContent {
            AutoTestSceneAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize(),
                        factory = { context ->
                            PreviewView(context).also {
                                previewView = it
                                startCameraIfReady()
                            }
                        }
                    )
                    if (showOverlayInfo) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(10.dp)
                        ) {
                            Text(text = "褰撳墠鍦烘櫙锛?sceneTitle", color = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "褰撳墠鐘舵€侊細$statusText", color = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "鎸?Back 杩斿洖", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    private fun startCameraIfReady() {
        if (isStopped) return
        if (previewView == null) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isStopped) return@addListener
            val provider = future.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView?.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview)
                statusText = "Running"
            }.onFailure {
                statusText = "Stopped (camera bind failed)"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    protected fun stopScene(shouldFinish: Boolean) {
        if (isStopped) {
            if (shouldFinish) finish()
            return
        }
        isStopped = true
        statusText = "Stopped"
        cameraProvider?.unbindAll()
        cameraProvider = null
        previewView = null
        if (shouldFinish) finish()
    }

    override fun onDestroy() {
        stopScene(shouldFinish = false)
        handler.removeCallbacksAndMessages(null)
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

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
