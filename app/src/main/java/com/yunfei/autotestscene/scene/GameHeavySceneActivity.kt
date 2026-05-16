package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yunfei.autotestscene.R
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.random.Random

private const val TAG = "AutoTestSceneApp"

private data class HeavyParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var alpha: Float,
    var color: Color
)

class GameHeavySceneActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val cpuRunning = AtomicBoolean(false)
    private val cpuWorkers = mutableListOf<Thread>()

    private var stopped = false
    private var statusText by mutableStateOf("Running")
    private var frameTick by mutableIntStateOf(0)
    private var score by mutableIntStateOf(0)
    private var playerX by mutableFloatStateOf(540f)
    private var playerY by mutableFloatStateOf(1200f)
    private var worldWidth = 1080f
    private var worldHeight = 1920f
    private var sceneStartMs = System.currentTimeMillis()

    private var particlesCount = 250
    private var cpuThreadsCount = 2
    private var audioEnabled = false
    private var vibrationEnabled = false

    private val particles = mutableListOf<HeavyParticle>()
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (stopped) return
            updateParticles()
            frameTick += 1
            score += 1
            handler.postDelayed(this, 16L)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene(shouldFinish = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        particlesCount = intent.getIntExtra(SceneIds.EXTRA_PARTICLES, 250).coerceIn(20, 1000)
        cpuThreadsCount = intent.getIntExtra(SceneIds.EXTRA_CPU_THREADS, 2).coerceIn(0, 8)
        audioEnabled = intent.getBooleanExtra(SceneIds.EXTRA_AUDIO, false)
        vibrationEnabled = intent.getBooleanExtra(SceneIds.EXTRA_VIBRATION, false)

        Log.d(TAG, "GameHeavySceneActivity started")
        Log.d(TAG, "particles count: $particlesCount")
        Log.d(TAG, "cpu_threads count: $cpuThreadsCount")
        Log.d(TAG, "audio enabled: $audioEnabled")
        Log.d(TAG, "vibration enabled: $vibrationEnabled")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersiveMode()
        registerStopReceiver()
        registerBackHandler()

        initParticles()
        startCpuWorkers()
        startAudioIfEnabled()
        startVibrationIfEnabled()
        handler.post(frameRunnable)

        setContent {
            AutoTestSceneAppTheme {
                GameHeavyScreen(
                    statusText = statusText,
                    particlesCount = particlesCount,
                    cpuThreads = cpuThreadsCount,
                    audioEnabled = audioEnabled,
                    vibrationEnabled = vibrationEnabled,
                    score = score,
                    runtimeSec = ((System.currentTimeMillis() - sceneStartMs) / 1000L).toInt(),
                    frameTick = frameTick,
                    particles = particles,
                    playerX = playerX,
                    playerY = playerY,
                    onTap = { x, y ->
                        playerX = x
                        playerY = y
                    },
                    onWorldSize = { w, h ->
                        worldWidth = w
                        worldHeight = h
                    },
                    onStopClick = { stopScene(shouldFinish = true) }
                )
            }
        }
    }

    private fun initParticles() {
        if (particles.isNotEmpty()) return
        repeat(particlesCount) {
            particles += HeavyParticle(
                x = Random.nextFloat() * worldWidth,
                y = Random.nextFloat() * worldHeight,
                vx = (Random.nextFloat() - 0.5f) * 7f,
                vy = (Random.nextFloat() - 0.5f) * 7f,
                radius = 2f + Random.nextFloat() * 6f,
                alpha = 0.35f + Random.nextFloat() * 0.65f,
                color = Color(
                    red = 0.3f + Random.nextFloat() * 0.7f,
                    green = 0.3f + Random.nextFloat() * 0.7f,
                    blue = 0.3f + Random.nextFloat() * 0.7f,
                    alpha = 1f
                )
            )
        }
    }

    private fun updateParticles() {
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.x <= 0f || p.x >= worldWidth) p.vx = -p.vx
            if (p.y <= 0f || p.y >= worldHeight) p.vy = -p.vy
            p.x = p.x.coerceIn(0f, worldWidth)
            p.y = p.y.coerceIn(0f, worldHeight)
        }
    }

    private fun startCpuWorkers() {
        if (cpuThreadsCount <= 0) return
        cpuRunning.set(true)
        repeat(cpuThreadsCount) { index ->
            val worker = Thread({
                Log.d(TAG, "CPU worker started: $index")
                var acc = 0.0
                while (cpuRunning.get()) {
                    for (i in 1..6000) {
                        val x = i + acc
                        acc += kotlin.math.sin(x) * kotlin.math.cos(x) + sqrt(x)
                        if (acc > 1e8) acc = 1.0
                    }
                    try {
                        Thread.sleep((5L..20L).random())
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                Log.d(TAG, "CPU worker stopped: $index")
            }, "GameHeavyCpu-$index")
            cpuWorkers += worker
            worker.start()
        }
    }

    private fun stopCpuWorkers() {
        cpuRunning.set(false)
        cpuWorkers.forEach { it.interrupt() }
        cpuWorkers.forEach {
            runCatching { it.join(300) }
        }
        cpuWorkers.clear()
    }

    private fun startAudioIfEnabled() {
        if (!audioEnabled) return
        runCatching {
            mediaPlayer = MediaPlayer.create(this, R.raw.test_music)?.apply {
                isLooping = true
                start()
            }
            if (mediaPlayer == null) {
                Log.e(TAG, "Failed to create MediaPlayer for game_heavy")
            }
        }.onFailure {
            Log.e(TAG, "Failed to start game_heavy audio", it)
        }
    }

    private fun stopAudio() {
        runCatching {
            mediaPlayer?.setOnErrorListener(null)
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.release()
        }.onFailure {
            Log.e(TAG, "Failed to stop audio", it)
        }
        mediaPlayer = null
    }

    private fun startVibrationIfEnabled() {
        if (!vibrationEnabled) return
        runCatching {
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            val vib = vibrator ?: return
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 800), 0))
        }.onFailure {
            Log.e(TAG, "Failed to start vibration", it)
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun stopScene(shouldFinish: Boolean) {
        Log.d(TAG, "stopScene called, shouldFinish=$shouldFinish")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }
        stopped = true
        statusText = "Stopped"
        handler.removeCallbacksAndMessages(null)
        stopCpuWorkers()
        stopAudio()
        stopVibration()
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

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@androidx.compose.runtime.Composable
private fun GameHeavyScreen(
    statusText: String,
    particlesCount: Int,
    cpuThreads: Int,
    audioEnabled: Boolean,
    vibrationEnabled: Boolean,
    score: Int,
    runtimeSec: Int,
    frameTick: Int,
    particles: List<HeavyParticle>,
    playerX: Float,
    playerY: Float,
    onTap: (Float, Float) -> Unit,
    onWorldSize: (Float, Float) -> Unit,
    onStopClick: () -> Unit
) {
    val fps = if (runtimeSec > 0) frameTick / runtimeSec else 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C16))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onTap(offset.x, offset.y)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            onWorldSize(size.width, size.height)

            val grid = 80f
            var gx = 0f
            while (gx < size.width) {
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += grid
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += grid
            }

            for (i in particles.indices) {
                val p = particles[i]
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
                if (i % 3 == 0 && i + 1 < particles.size) {
                    val q = particles[i + 1]
                    val dx = p.x - q.x
                    val dy = p.y - q.y
                    val dist = kotlin.math.abs(dx) + kotlin.math.abs(dy)
                    if (dist < 220f) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(p.x, p.y),
                            end = Offset(q.x, q.y),
                            strokeWidth = 1f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            drawCircle(color = Color(0xFF00E5FF), radius = 22f, center = Offset(playerX, playerY))
            drawCircle(color = Color(0xFFFFFFFF), radius = 8f, center = Offset(playerX, playerY))

            val t = frameTick * 0.03f
            repeat(4) { idx ->
                val ex = size.width * (0.2f + idx * 0.18f) + kotlin.math.sin(t + idx) * 45f
                val ey = size.height * (0.2f + idx * 0.14f) + kotlin.math.cos(t + idx * 1.7f) * 35f
                drawCircle(color = Color(0xFFFF5252), radius = 14f + idx * 2f, center = Offset(ex, ey))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(12.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Current scene: game load", color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Status: $statusText", color = Color.White)
            Text(text = "Particles: $particlesCount", color = Color.White)
            Text(text = "CPU threads: $cpuThreads", color = Color.White)
            Text(text = "Audio: ${if (audioEnabled) "ON" else "OFF"}", color = Color.White)
            Text(text = "Vibration: ${if (vibrationEnabled) "ON" else "OFF"}", color = Color.White)
            Text(text = "Score: $score  FPS:$fps  Runtime:${runtimeSec}s", color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Hint: Back / STOP broadcast to stop", color = Color.White)
        }

        Button(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            onClick = onStopClick
        ) {
            Text("鍋滄")
        }
    }
}
