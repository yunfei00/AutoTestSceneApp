package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import com.yunfei.autotestscene.R
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

class MusicSceneActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
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
        registerStopReceiver()
        startMusic()

        setContent {
            AutoTestSceneAppTheme {
                MusicScreen(
                    statusText = statusText,
                    onStopClick = { stopScene() }
                )
            }
        }
    }

    private fun updateStatus(status: String) {
        statusText = status
    }

    private fun startMusic() {
        try {
            stopMusicOnly()

            mediaPlayer = MediaPlayer.create(this, R.raw.test_music)
            if (mediaPlayer == null) {
                updateStatus("Error")
                Log.e("AutoTestSceneApp", "Failed to create MediaPlayer for test_music.mp3")
                return
            }

            mediaPlayer?.apply {
                isLooping = true
                setOnCompletionListener {
                    try {
                        seekTo(0)
                        start()
                    } catch (e: Exception) {
                        Log.e("AutoTestSceneApp", "Failed to restart music on completion", e)
                        updateStatus("Error")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AutoTestSceneApp", "MediaPlayer error what=$what extra=$extra")
                    updateStatus("Error")
                    true
                }
                start()
            }

            updateStatus("Running")
            Log.d("AutoTestSceneApp", "MusicSceneActivity started playing R.raw.test_music")
        } catch (e: Exception) {
            Log.e("AutoTestSceneApp", "Failed to start music", e)
            updateStatus("Error")
        }
    }

    private fun stopMusicOnly() {
        try {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {
            Log.e("AutoTestSceneApp", "Failed to stop music", e)
        }

        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AutoTestSceneApp", "Failed to release MediaPlayer", e)
        }

        mediaPlayer = null
    }

    private fun stopScene() {
        if (stopped) return
        stopped = true
        updateStatus("Stopped")
        stopMusicOnly()
        finish()
    }

    override fun onDestroy() {
        stopMusicOnly()
        unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    private fun registerStopReceiver() {
        registerNotExportedReceiver(stopReceiver, IntentFilter(SceneIds.ACTION_STOP))
    }
}

@androidx.compose.runtime.Composable
private fun MusicScreen(
    statusText: String,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Current scene: music playback")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Status: $statusText")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Audio resource: test_music.mp3")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Stop: tap the button or send STOP broadcast")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopClick) {
            Text("Stop")
        }
    }
}
