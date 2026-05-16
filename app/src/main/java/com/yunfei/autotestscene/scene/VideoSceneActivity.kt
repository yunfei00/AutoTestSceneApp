package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yunfei.autotestscene.R
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

class VideoSceneActivity : ComponentActivity() {
    private var videoView: VideoView? = null
    private var stopped = false
    private var statusText by mutableStateOf("Stopped")

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
        registerStopReceiver()
        registerBackHandler()

        setContent {
            AutoTestSceneAppTheme {
                VideoScreen(
                    bindVideoView = { view ->
                        videoView = view
                        startVideo()
                    }
                )
            }
        }
    }

    private fun updateStatus(status: String) {
        statusText = status
    }

    private fun startVideo() {
        val vv = videoView ?: return
        try {
            stopVideoOnly()
            val uri = Uri.parse("android.resource://$packageName/${R.raw.test_video}")
            vv.setVideoURI(uri)
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                updateStatus("Running")
                vv.start()
                Log.d("AutoTestSceneApp", "VideoSceneActivity started playing R.raw.test_video")
            }
            vv.setOnCompletionListener {
                try {
                    vv.seekTo(0)
                    vv.start()
                } catch (e: Exception) {
                    Log.e("AutoTestSceneApp", "Failed to restart video on completion", e)
                    updateStatus("Error")
                }
            }
            vv.setOnErrorListener { _, what, extra ->
                Log.e("AutoTestSceneApp", "VideoView error what=$what extra=$extra")
                updateStatus("Error")
                true
            }
            vv.start()
        } catch (e: Exception) {
            Log.e("AutoTestSceneApp", "Failed to start video", e)
            updateStatus("Error")
        }
    }

    private fun stopVideoOnly() {
        val vv = videoView ?: return
        try {
            vv.setOnPreparedListener(null)
            vv.setOnCompletionListener(null)
            vv.setOnErrorListener(null)
            vv.stopPlayback()
        } catch (e: Exception) {
            Log.e("AutoTestSceneApp", "Failed to stop video", e)
        }
    }

    private fun stopScene() {
        if (stopped) return
        stopped = true
        updateStatus("Stopped")
        stopVideoOnly()
        finish()
    }

    override fun onDestroy() {
        stopVideoOnly()
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
}

@androidx.compose.runtime.Composable
private fun VideoScreen(
    bindVideoView: (VideoView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).also {
                bindVideoView(it)
            }
        }
    )
}
