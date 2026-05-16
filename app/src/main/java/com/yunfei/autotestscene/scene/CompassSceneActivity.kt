package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.yunfei.autotestscene.util.registerNotExportedReceiver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme
import kotlin.math.roundToInt

private const val TAG = "AutoTestSceneApp"

class CompassSceneActivity : ComponentActivity(), SensorEventListener {
    private var stopped = false
    private var statusText by mutableStateOf("Stopped")
    private var azimuth by mutableFloatStateOf(0f)
    private var directionText by mutableStateOf("N")
    private var sensorStatus by mutableStateOf("Accelerometer N/A / MagneticField N/A")

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magneticField: Sensor? = null
    private var accelValues = FloatArray(3)
    private var magValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

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
        startSensors()

        setContent {
            AutoTestSceneAppTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "褰撳墠鍦烘櫙锛氭寚鍗楅拡")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "褰撳墠鐘舵€侊細$statusText")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "鏂逛綅瑙掞細${azimuth.roundToInt()}掳")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "鏂瑰悜鏂囧瓧锛?directionText")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "浼犳劅鍣ㄧ姸鎬侊細$sensorStatus")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "鍋滄鏂瑰紡锛欱ack 杩斿洖 / STOP 骞挎挱 / 鍋滄鎸夐挳")
                    Spacer(modifier = Modifier.height(12.dp))
                    Canvas(modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension * 0.35f
                        drawCircle(Color(0xFF1E2A3A), radius = radius, center = center)
                        drawCircle(Color(0x553FA9F5), radius = radius * 0.88f, center = center)
                        drawLine(Color.White, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + 28f), 4f, StrokeCap.Round)
                        rotate(degrees = -azimuth, pivot = center) {
                            drawLine(
                                color = Color.Red,
                                start = center,
                                end = Offset(center.x, center.y - radius * 0.8f),
                                strokeWidth = 8f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { stopScene(shouldFinish = true) }) {
                        Text("鍋滄")
                    }
                }
            }
        }
    }

    private fun startSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticField = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer == null || magneticField == null) {
            statusText = "Error"
            sensorStatus = "Unsupported"
            return
        }
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager?.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_GAME)
        statusText = "Running"
        sensorStatus = "Accelerometer OK / MagneticField OK"
        Log.d(TAG, "compass sensor registered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
                hasAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magValues = event.values.clone()
                hasMag = true
            }
        }
        if (!hasAccel || !hasMag) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        val ok = SensorManager.getRotationMatrix(r, i, accelValues, magValues)
        if (!ok) return
        val orientation = FloatArray(3)
        SensorManager.getOrientation(r, orientation)
        val degree = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
        azimuth = degree
        directionText = degreeToDirection(degree)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun degreeToDirection(degree: Float): String {
        val d = ((degree + 22.5f) % 360f / 45f).toInt()
        return listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[d]
    }

    private fun stopScene(shouldFinish: Boolean) {
        Log.d(TAG, "compass stopScene called")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }
        stopped = true
        statusText = "Stopped"
        runCatching { sensorManager?.unregisterListener(this) }
        Log.d(TAG, "compass sensor unregistered")
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
