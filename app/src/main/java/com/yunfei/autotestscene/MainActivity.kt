package com.yunfei.autotestscene

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunfei.autotestscene.scene.SceneIds
import com.yunfei.autotestscene.scene.SceneRouter
import com.yunfei.autotestscene.ui.theme.AutoTestSceneAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoTestSceneAppTheme {
                DebugEntryScreen(
                    onStartMotor = { SceneRouter.openScene(this, SceneIds.SCENE_MOTOR) },
                    onStartMusic = { SceneRouter.openScene(this, SceneIds.SCENE_MUSIC) },
                    onStartVideo = { SceneRouter.openScene(this, SceneIds.SCENE_VIDEO) },
                    onStartFlashlight = { SceneRouter.openScene(this, SceneIds.SCENE_FLASHLIGHT) },
                    onStartRecorder = { SceneRouter.openScene(this, SceneIds.SCENE_RECORDER) },
                    onStartMirror = { SceneRouter.openScene(this, SceneIds.SCENE_MIRROR) },
                    onStartCompass = { SceneRouter.openScene(this, SceneIds.SCENE_COMPASS) },
                    onStartAmbientLight = { SceneRouter.openScene(this, SceneIds.SCENE_AMBIENT_LIGHT) },
                    onStartWhiteScreen = { SceneRouter.openScene(this, SceneIds.SCENE_WHITE_SCREEN) },
                    onStartDynamicWallpaper = {
                        SceneRouter.openScene(
                            this,
                            SceneIds.SCENE_DYNAMIC_WALLPAPER,
                            style = SceneIds.STYLE_DANDELION
                        )
                    },
                    onStartGameHeavy = {
                        SceneRouter.openScene(
                            this,
                            SceneIds.SCENE_GAME_HEAVY,
                            particles = 250,
                            cpuThreads = 2,
                            audio = false,
                            vibration = false
                        )
                    },
                    onStartFrontCamera = { SceneRouter.openScene(this, SceneIds.SCENE_FRONT_CAMERA) },
                    onStartRearCamera = { SceneRouter.openScene(this, SceneIds.SCENE_REAR_CAMERA) },
                    onStopScene = {
                        sendBroadcast(Intent(SceneIds.ACTION_STOP).apply {
                            setPackage(packageName)
                        })
                    }
                )
            }
        }
    }
}

@Composable
private fun DebugEntryScreen(
    onStartMotor: () -> Unit,
    onStartMusic: () -> Unit,
    onStartVideo: () -> Unit,
    onStartFlashlight: () -> Unit,
    onStartRecorder: () -> Unit,
    onStartMirror: () -> Unit,
    onStartCompass: () -> Unit,
    onStartAmbientLight: () -> Unit,
    onStartWhiteScreen: () -> Unit,
    onStartDynamicWallpaper: () -> Unit,
    onStartGameHeavy: () -> Unit,
    onStartFrontCamera: () -> Unit,
    onStartRearCamera: () -> Unit,
    onStopScene: () -> Unit
) {
    val sceneButtons = listOf(
        "马达" to onStartMotor,
        "音乐" to onStartMusic,
        "视频" to onStartVideo,
        "手电筒" to onStartFlashlight,
        "录音机" to onStartRecorder,
        "镜子" to onStartMirror,
        "指南针" to onStartCompass,
        "启动环境光测试" to onStartAmbientLight,
        "白屏" to onStartWhiteScreen,
        "动态壁纸" to onStartDynamicWallpaper,
        "游戏高负载" to onStartGameHeavy,
        "前置主摄" to onStartFrontCamera,
        "后置主摄" to onStartRearCamera,
        "停止场景" to onStopScene
    )

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
        columns = GridCells.Adaptive(minSize = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sceneButtons) { item ->
            Button(onClick = item.second) {
                Text(item.first)
            }
        }
    }
}
