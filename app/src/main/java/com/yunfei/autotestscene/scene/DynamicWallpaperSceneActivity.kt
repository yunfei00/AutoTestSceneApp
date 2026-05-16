package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme
import kotlin.math.sin
import kotlin.random.Random

private data class SeedParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var baseY: Float,
    var waveOffset: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float,
    var scale: Float
)

class DynamicWallpaperSceneActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var isStopped = false
    private var style = SceneIds.STYLE_DANDELION
    private var statusText by mutableStateOf("Running")
    private var frameTick by mutableIntStateOf(0)
    private var canvasWidth = 1080f
    private var canvasHeight = 1920f
    private val particles = mutableListOf<SeedParticle>()

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (isStopped) return
            updateParticles()
            frameTick += 1
            handler.postDelayed(this, 33L)
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
        style = intent.getStringExtra(SceneIds.EXTRA_STYLE)?.ifBlank { SceneIds.STYLE_DANDELION }
            ?: SceneIds.STYLE_DANDELION
        if (style !in setOf(
                SceneIds.STYLE_DANDELION,
                SceneIds.STYLE_STARS,
                SceneIds.STYLE_SAKURA,
                SceneIds.STYLE_RAIN,
                SceneIds.STYLE_PARTICLES
            )
        ) {
            style = SceneIds.STYLE_DANDELION
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        registerStopReceiver()
        initParticles()
        handler.post(frameRunnable)

        setContent {
            AutoTestSceneAppTheme {
                DynamicWallpaperScreen(
                    style = style,
                    statusText = statusText,
                    frameTick = frameTick,
                    particles = particles,
                    onCanvasSizeChanged = { width, height ->
                        canvasWidth = width
                        canvasHeight = height
                    },
                    onStopClick = { stopScene(shouldFinish = true) }
                )
            }
        }
    }

    private fun initParticles() {
        if (particles.isNotEmpty()) return
        repeat(35) {
            particles.add(
                createSeedParticle(
                    width = canvasWidth,
                    height = canvasHeight,
                    randomSpawn = true
                )
            )
        }
    }

    private fun updateParticles() {
        for (i in particles.indices) {
            val p = particles[i]
            p.x += p.vx
            p.baseY += p.vy
            p.y = p.baseY + sin(frameTick * 0.03f + p.waveOffset) * 0.4f
            p.rotation += p.rotationSpeed
            if (p.rotation > 360f) {
                p.rotation -= 360f
            } else if (p.rotation < -360f) {
                p.rotation += 360f
            }
            if (p.x > canvasWidth + 40f || p.y < -40f) {
                particles[i] = createSeedParticle(canvasWidth, canvasHeight, randomSpawn = false)
            }
        }
    }

    private fun createSeedParticle(width: Float, height: Float, randomSpawn: Boolean): SeedParticle {
        val fromFlowerArea = randomSpawn && Random.nextFloat() < 0.45f
        val spawnFromLeft = Random.nextBoolean()
        val x = if (fromFlowerArea) {
            width * (0.08f + Random.nextFloat() * 0.20f)
        } else if (randomSpawn) {
            width * (0.1f + Random.nextFloat() * 0.4f)
        } else if (spawnFromLeft) {
            Random.nextFloat() * (width * 0.15f)
        } else {
            width * (0.08f + Random.nextFloat() * 0.30f)
        }
        val y = if (randomSpawn) {
            height * (0.2f + Random.nextFloat() * 0.65f)
        } else {
            height * (0.70f + Random.nextFloat() * 0.25f)
        }
        return SeedParticle(
            x = x,
            y = y,
            vx = 0.3f + Random.nextFloat() * 1.2f,
            vy = -(0.2f + Random.nextFloat() * 0.8f),
            baseY = y,
            waveOffset = Random.nextFloat() * 6.28f,
            rotation = Random.nextFloat() * 360f,
            rotationSpeed = -1.6f + Random.nextFloat() * 3.2f,
            alpha = 0.35f + Random.nextFloat() * 0.55f,
            scale = 0.6f + Random.nextFloat() * 0.8f
        )
    }

    private fun stopScene(shouldFinish: Boolean) {
        if (isStopped) {
            if (shouldFinish) finish()
            return
        }
        isStopped = true
        statusText = "Stopped"
        handler.removeCallbacksAndMessages(null)
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
private fun DynamicWallpaperScreen(
    style: String,
    statusText: String,
    frameTick: Int,
    particles: List<SeedParticle>,
    onCanvasSizeChanged: (Float, Float) -> Unit,
    onStopClick: () -> Unit
) {
    val styleName = when (style) {
        SceneIds.STYLE_DANDELION -> "Dandelion"
        SceneIds.STYLE_STARS -> "Stars"
        SceneIds.STYLE_SAKURA -> "Sakura"
        SceneIds.STYLE_RAIN -> "Rain"
        SceneIds.STYLE_PARTICLES -> "Particles"
        else -> "Dandelion"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF7FF))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            onCanvasSizeChanged(size.width, size.height)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8CCEFF),
                        Color(0xFFCFEAFF),
                        Color(0xFFF3FAFF)
                    )
                )
            )

            repeat(6) { i ->
                val r = 80f + i * 26f
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f - i * 0.008f),
                    radius = r,
                    center = Offset(
                        x = size.width * (0.15f + i * 0.12f),
                        y = size.height * (0.12f + (i % 3) * 0.08f)
                    )
                )
            }

            val stemColor = Color(0x995A7D5A)
            val coreColor = Color(0xDDF8FBFF)
            val dandelions = listOf(
                Triple(Offset(size.width * 0.10f, size.height * 0.95f), 250f, 30f),
                Triple(Offset(size.width * 0.22f, size.height * 0.96f), 190f, 22f),
                Triple(Offset(size.width * 0.05f, size.height * 0.97f), 160f, 20f)
            )
            dandelions.forEachIndexed { index, spec ->
                val root = spec.first
                val stemLen = spec.second
                val bloomRadius = spec.third
                val head = Offset(root.x + 10f * index, root.y - stemLen)
                drawLine(
                    color = stemColor,
                    start = root,
                    end = head,
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = coreColor, radius = bloomRadius * 0.35f, center = head)
                repeat(22) { k ->
                    val angle = k * (360f / 22f)
                    rotate(degrees = angle, pivot = head) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f),
                            start = head,
                            end = Offset(head.x, head.y - bloomRadius),
                            strokeWidth = 1.2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            particles.forEachIndexed { index, p ->
                val pulse = 0.70f + 0.30f * sin((frameTick + index * 9) * 0.04f)
                val alpha = (p.alpha * pulse).coerceIn(0.15f, 1f)
                val stem = 18f * p.scale
                rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(p.x, p.y),
                        end = Offset(p.x - stem, p.y + stem * 0.55f),
                        strokeWidth = 1.4f * p.scale,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        start = Offset(p.x, p.y),
                        end = Offset(p.x - stem * 0.55f, p.y - stem * 0.35f),
                        strokeWidth = 1.1f * p.scale,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        center = Offset(p.x, p.y),
                        radius = 3.2f * p.scale
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(text = "Current scene: dynamic wallpaper", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Style: $styleName", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Status: $statusText", color = Color.White)
            }
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
