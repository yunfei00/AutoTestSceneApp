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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme
import java.util.Locale

private const val TAG = "AutoTestSceneApp"

class AmbientLightSceneActivity : ComponentActivity(), SensorEventListener {
    private var stopped = false
    private var receiverRegistered = false

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null

    private var sampleCount = 0L
    private var minLux = Float.MAX_VALUE
    private var maxLux = Float.MIN_VALUE
    private var sumLux = 0.0

    private var statusText by mutableStateOf("Stopped")
    private var currentLuxText by mutableStateOf("-- lux")
    private var lightLevelText by mutableStateOf("--")
    private var minLuxText by mutableStateOf("-- lux")
    private var maxLuxText by mutableStateOf("-- lux")
    private var avgLuxText by mutableStateOf("-- lux")
    private var sampleCountText by mutableStateOf("0")
    private var sensorNameText by mutableStateOf("N/A")
    private var sensorVendorText by mutableStateOf("N/A")
    private var sensorPowerText by mutableStateOf("N/A")
    private var sensorMaxRangeText by mutableStateOf("N/A")

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SceneIds.ACTION_STOP) {
                stopScene(shouldFinish = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AmbientLightSceneActivity started")

        registerStopReceiver()
        registerBackHandler()
        startLightSensor()

        setContent {
            AutoTestSceneAppTheme {
                AmbientLightScreen(
                    statusText = statusText,
                    currentLuxText = currentLuxText,
                    lightLevelText = lightLevelText,
                    minLuxText = minLuxText,
                    maxLuxText = maxLuxText,
                    avgLuxText = avgLuxText,
                    sampleCountText = sampleCountText,
                    sensorNameText = sensorNameText,
                    sensorVendorText = sensorVendorText,
                    sensorPowerText = sensorPowerText,
                    sensorMaxRangeText = sensorMaxRangeText,
                    onStopClick = { stopScene(shouldFinish = true) }
                )
            }
        }
    }

    private fun startLightSensor() {
        runCatching {
            val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: run {
                statusText = "Error"
                Log.e(TAG, "light sensor manager unavailable")
                return
            }
            sensorManager = manager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
            lightSensor = sensor

            if (sensor == null) {
                statusText = "Unsupported"
                currentLuxText = "Unsupported"
                lightLevelText = "Unsupported"
                Log.d(TAG, "light sensor unsupported")
                return
            }

            sensorNameText = sensor.name
            sensorVendorText = sensor.vendor
            sensorPowerText = "${formatLux(sensor.power)} mA"
            sensorMaxRangeText = "${formatLux(sensor.maximumRange)} lux"

            val registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            if (!registered) {
                statusText = "Error"
                Log.d(TAG, "light sensor unsupported")
                return
            }

            statusText = "Running"
            Log.d(TAG, "light sensor registered")
        }.onFailure {
            statusText = "Error"
            Log.e(TAG, "light sensor start error", it)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LIGHT || stopped) return

        val lux = event.values.firstOrNull() ?: return
        sampleCount += 1
        minLux = minOf(minLux, lux)
        maxLux = maxOf(maxLux, lux)
        sumLux += lux.toDouble()
        val avgLux = sumLux / sampleCount

        currentLuxText = "${formatLux(lux)} lux"
        lightLevelText = lightLevel(lux)
        minLuxText = "${formatLux(minLux)} lux"
        maxLuxText = "${formatLux(maxLux)} lux"
        avgLuxText = "${formatLux(avgLux)} lux"
        sampleCountText = sampleCount.toString()

        Log.d(TAG, "lux updated: $lux")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun stopScene(shouldFinish: Boolean = true) {
        Log.d(TAG, "stopScene called")
        if (stopped) {
            if (shouldFinish) finish()
            return
        }

        stopped = true
        statusText = "Stopped"
        runCatching {
            sensorManager?.unregisterListener(this)
            Log.d(TAG, "light sensor unregistered")
        }.onFailure {
            Log.e(TAG, "light sensor unregister error", it)
        }

        if (shouldFinish) finish()
    }

    override fun onDestroy() {
        stopScene(shouldFinish = false)
        if (receiverRegistered) {
            unregisterReceiver(stopReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun registerStopReceiver() {
        registerNotExportedReceiver(stopReceiver, IntentFilter(SceneIds.ACTION_STOP))
        receiverRegistered = true
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopScene(shouldFinish = true)
            }
        })
    }

    private fun lightLevel(lux: Float): String {
        return when {
            lux < 10f -> "Dark"
            lux < 100f -> "Dim"
            lux < 1000f -> "Indoor"
            lux < 10000f -> "Bright"
            else -> "Intense"
        }
    }

    private fun formatLux(value: Float): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatLux(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }
}

@Composable
private fun AmbientLightScreen(
    statusText: String,
    currentLuxText: String,
    lightLevelText: String,
    minLuxText: String,
    maxLuxText: String,
    avgLuxText: String,
    sampleCountText: String,
    sensorNameText: String,
    sensorVendorText: String,
    sensorPowerText: String,
    sensorMaxRangeText: String,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Current scene: ambient light test")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Status: $statusText")
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = currentLuxText,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Light level: $lightLevelText",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))
        AmbientLightInfoText(label = "Min lux", value = minLuxText)
        AmbientLightInfoText(label = "Max lux", value = maxLuxText)
        AmbientLightInfoText(label = "Average lux", value = avgLuxText)
        AmbientLightInfoText(label = "Sample count", value = sampleCountText)
        AmbientLightInfoText(label = "Sensor name", value = sensorNameText)
        AmbientLightInfoText(label = "Sensor vendor", value = sensorVendorText)
        AmbientLightInfoText(label = "Sensor power", value = sensorPowerText)
        AmbientLightInfoText(label = "Sensor max range", value = sensorMaxRangeText)
        AmbientLightInfoText(label = "Stop", value = "Back / STOP broadcast / stop button")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopClick) {
            Text("Stop")
        }
    }
}

@Composable
private fun AmbientLightInfoText(label: String, value: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "$label: $value")
}
