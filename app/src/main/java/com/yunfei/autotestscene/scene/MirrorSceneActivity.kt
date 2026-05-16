package com.yunfei.autotestscene.scene

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

private const val TAG = "AutoTestSceneApp"

class MirrorSceneActivity : ComponentActivity() {
    private var cameraId: String = "N/A"
    private var statusText by mutableStateOf("Stopped")
    private var overlayVisible by mutableStateOf(true)
    private var stopped = false

    private var textureView: TextureView? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene(shouldFinish = true)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCameraIfReady() else statusText = "Error"
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCameraIfReady()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            statusText = "Running"
            Log.d(TAG, "mirror camera opened: $cameraId")
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            if (!stopped) statusText = "Error"
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "mirror camera error: $error")
            camera.close()
            if (!stopped) statusText = "Error"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        registerStopReceiver()
        registerBackHandler()
        startCameraThread()
        cameraId = findFrontCameraId() ?: "N/A"
        if (cameraId == "N/A") statusText = "Error"

        setContent {
            AutoTestSceneAppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { overlayVisible = !overlayVisible }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            TextureView(context).also {
                                it.scaleX = -1f
                                textureView = it
                                if (it.isAvailable) openCameraIfReady() else it.surfaceTextureListener = surfaceListener
                            }
                        }
                    )

                    if (overlayVisible) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text("Current scene: mirror", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Status: $statusText", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Camera ID: $cameraId", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Hint: Back / STOP broadcast to stop", color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { stopScene(shouldFinish = true) }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findFrontCameraId(): String? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrNull()
    }

    private fun openCameraIfReady() {
        if (stopped || cameraId == "N/A") return
        val tv = textureView ?: return
        if (!tv.isAvailable) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        runCatching { manager.openCamera(cameraId, stateCallback, cameraHandler) }
            .onFailure {
                statusText = "Error"
                Log.e(TAG, "mirror open camera failed", it)
            }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        st.setDefaultBufferSize(tv.width.coerceAtLeast(720), tv.height.coerceAtLeast(1280))
        val surface = Surface(st)
        runCatching {
            previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    runCatching {
                        session.setRepeatingRequest(previewBuilder?.build()!!, null, cameraHandler)
                    }.onFailure {
                        statusText = "Error"
                        Log.e(TAG, "mirror start preview failed", it)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    statusText = "Error"
                }
            }, cameraHandler)
        }.onFailure {
            statusText = "Error"
            Log.e(TAG, "mirror create session failed", it)
        }
    }

    private fun closeCamera() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        previewBuilder = null
        Log.d(TAG, "mirror camera closed")
    }

    private fun startCameraThread() {
        if (cameraThread != null) return
        cameraThread = HandlerThread("MirrorCameraThread").also {
            it.start()
            cameraHandler = Handler(it.looper)
        }
    }

    private fun stopCameraThread() {
        val t = cameraThread ?: return
        t.quitSafely()
        runCatching { t.join() }
        cameraThread = null
        cameraHandler = null
    }

    private fun stopScene(shouldFinish: Boolean) {
        Log.d(TAG, "mirror stopScene called")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }
        stopped = true
        statusText = "Stopped"
        closeCamera()
        stopCameraThread()
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
