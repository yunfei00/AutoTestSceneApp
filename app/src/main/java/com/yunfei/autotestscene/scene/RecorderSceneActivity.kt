package com.yunfei.autotestscene.scene

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AutoTestSceneApp"

class RecorderSceneActivity : ControlledSceneActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var temporaryRecording: File? = null
    private var recordingStarted = false
    private var stopped = false
    private var startAtMs: Long = 0L
    private var statusText by mutableStateOf("Stopped")
    private var recordPath by mutableStateOf("N/A")
    private var durationSec by mutableIntStateOf(0)

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (stopped || startAtMs == 0L) return
            durationSec = ((System.currentTimeMillis() - startAtMs) / 1000L).toInt()
            handler.postDelayed(this, 1000L)
        }
    }

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
        if (granted) {
            startRecording()
        } else {
            statusText = "Error"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerStopReceiver()
        registerBackHandler()
        ensurePermissionThenStart()

        setContent {
            AutoTestSceneAppTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Current scene: recorder")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Status: $statusText")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Record file: $recordPath")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Duration: ${durationSec}s")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Stop: Back / STOP broadcast / stop button")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { stopScene(shouldFinish = true) }) {
                        Text("Stop")
                    }
                }
            }
        }
    }

    private fun ensurePermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else if (isControlledScene) {
            statusText = "Microphone permission required"
            resourceError("MIC_PERMISSION_REQUIRED")
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        runCatching {
            val outDir = File(getExternalFilesDir("recordings") ?: filesDir, "recordings")
            outDir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = if (isControlledScene) {
                File.createTempFile("gnss-recorder-", ".m4a", cacheDir).also { temporaryRecording = it }
            } else {
                File(outDir, "recorder_${ts}.m4a")
            }
            recordPath = if (isControlledScene) "Temporary audio · local only" else outFile.absolutePath

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = mr
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            recordingStarted = true
            startAtMs = System.currentTimeMillis()
            durationSec = 0
            handler.post(tickRunnable)
            statusText = "Running"
            resourceReady()
            Log.d(TAG, "recorder started, file=$recordPath")
        }.onFailure {
            statusText = "Error"
            Log.e(TAG, "Failed to start recorder", it)
            resourceError("RECORDING_START_FAILED")
        }
    }

    private fun stopRecorderOnly() {
        handler.removeCallbacksAndMessages(null)
        if (recordingStarted) {
            runCatching { recorder?.stop() }.onFailure {
                Log.e(TAG, "MediaRecorder stop failed", it)
                resourceError("RECORDING_STOP_FAILED")
            }
        }
        recordingStarted = false
        runCatching {
            recorder?.release()
        }.onFailure {
            Log.e(TAG, "MediaRecorder release failed", it)
            resourceError("RECORDING_RELEASE_FAILED")
        }
        recorder = null
        temporaryRecording?.let {
            if (it.exists() && !it.delete()) resourceError("RECORDING_TEMP_CLEANUP_FAILED")
        }
        temporaryRecording = null
    }

    private fun stopScene(shouldFinish: Boolean) {
        Log.d(TAG, "recorder stopScene called")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }
        stopped = true
        statusText = "Stopped"
        stopRecorderOnly()
        Log.d(TAG, "recorder stopped, file=$recordPath")
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
