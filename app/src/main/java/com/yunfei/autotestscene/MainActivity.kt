package com.yunfei.autotestscene

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yunfei.autotestscene.scene.OperatorSceneState
import com.yunfei.autotestscene.scene.SceneControl
import com.yunfei.autotestscene.scene.SceneIds
import com.yunfei.autotestscene.scene.SceneRouter
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

private val commonScenes = listOf(
    SceneIds.SCENE_FRONT_CAMERA to R.string.operator_front,
    SceneIds.SCENE_REAR_CAMERA to R.string.operator_rear,
    SceneIds.SCENE_VIDEO to R.string.operator_video,
    SceneIds.SCENE_MUSIC to R.string.operator_music,
    SceneIds.SCENE_MOTOR to R.string.operator_motor,
    SceneIds.SCENE_FLASHLIGHT to R.string.operator_flashlight,
    SceneIds.SCENE_RECORDER to R.string.operator_recorder,
    SceneIds.SCENE_WHITE_SCREEN to R.string.operator_white,
)
private val extraScenes = listOf(
    SceneIds.SCENE_COMPASS to R.string.operator_compass,
    SceneIds.SCENE_AMBIENT_LIGHT to R.string.operator_light,
    SceneIds.SCENE_GAME_HEAVY to R.string.operator_game,
    SceneIds.SCENE_MIRROR to R.string.operator_mirror,
    SceneIds.SCENE_DYNAMIC_WALLPAPER to R.string.operator_wallpaper,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
        setContent {
            AutoTestSceneAppTheme(dynamicColor = false) {
                val state by SceneControl.operatorState.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    SceneHome(state, onStart = { scene ->
                        SceneControl.openManual(scene) {
                            SceneRouter.openScene(
                                this, scene,
                                style = if (scene == SceneIds.SCENE_DYNAMIC_WALLPAPER) SceneIds.STYLE_DANDELION else null,
                                operator = true,
                            )
                        }
                    }, onStop = SceneControl::stopFromOperator)
                }
            }
        }
    }
}

@Composable
private fun SceneHome(state: OperatorSceneState, onStart: (String) -> Unit, onStop: () -> Unit) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    val running = state.status in setOf("STARTING", "READY", "STOPPING")
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.operator_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SceneStatus(state, running, onStop)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.operator_common),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(commonScenes, key = { it.first }) { (scene, title) ->
            OutlinedButton(onClick = { onStart(scene) }, enabled = !running) { Text(stringResource(title)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showMore) R.string.operator_less else R.string.operator_more))
            }
        }
        if (showMore) {
            items(extraScenes, key = { it.first }) { (scene, title) ->
                OutlinedButton(onClick = { onStart(scene) }, enabled = !running) { Text(stringResource(title)) }
            }
        }
    }
}

@Composable
private fun SceneStatus(state: OperatorSceneState, running: Boolean, onStop: () -> Unit) {
    val status = when (state.status) {
        "STARTING" -> R.string.operator_starting
        "READY" -> R.string.operator_ready
        "STOPPING" -> R.string.operator_stopping
        "STOPPED" -> R.string.operator_stopped
        "ERROR" -> R.string.operator_error
        else -> R.string.operator_idle
    }
    val scene = (commonScenes + extraScenes).firstOrNull { it.first == state.scene }?.second
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    stringResource(status),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.status == "ERROR") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (scene != null) Text(stringResource(scene))
                if (state.reason != null) Text(stringResource(sceneReason(state.reason)))
            }
            if (running) {
                Button(
                    onClick = onStop, enabled = state.status != "STOPPING",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.operator_stop)) }
            }
        }
    }
}

private fun sceneReason(reason: String): Int = when (reason) {
    "CAMERA_PERMISSION_REQUIRED" -> R.string.operator_camera_permission
    "MIC_PERMISSION_REQUIRED" -> R.string.operator_mic_permission
    "TORCH_UNAVAILABLE", "VIBRATOR_UNAVAILABLE", "SENSOR_UNAVAILABLE",
    "CAMERA_UNAVAILABLE" -> R.string.operator_hardware_unavailable
    "READY_TIMEOUT" -> R.string.operator_timeout
    "SCENE_BACKGROUND", "SCENE_EXITED" -> R.string.operator_scene_exited
    else -> R.string.operator_try_again
}
