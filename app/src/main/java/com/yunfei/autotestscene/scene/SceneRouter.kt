package com.yunfei.autotestscene.scene

import android.content.Context
import android.content.Intent
import com.yunfei.autotestscene.util.ToastUtil

object SceneRouter {
    fun openScene(
        context: Context,
        sceneId: String?,
        duration: Int = 0,
        style: String? = null,
        particles: Int = 250,
        cpuThreads: Int = 2,
        audio: Boolean = false,
        vibration: Boolean = false
    ) {
        if (sceneId == SceneIds.SCENE_GAME_LIGHT || sceneId == SceneIds.SCENE_GAME_2D) {
            ToastUtil.show(context, "该游戏场景暂未实现，请使用 game_heavy")
            return
        }

        val target = when (sceneId) {
            SceneIds.SCENE_MOTOR -> MotorSceneActivity::class.java
            SceneIds.SCENE_MUSIC -> MusicSceneActivity::class.java
            SceneIds.SCENE_VIDEO -> VideoSceneActivity::class.java
            SceneIds.SCENE_FLASHLIGHT -> FlashlightSceneActivity::class.java
            SceneIds.SCENE_RECORDER -> RecorderSceneActivity::class.java
            SceneIds.SCENE_MIRROR -> MirrorSceneActivity::class.java
            SceneIds.SCENE_COMPASS -> CompassSceneActivity::class.java
            SceneIds.SCENE_AMBIENT_LIGHT -> AmbientLightSceneActivity::class.java
            SceneIds.SCENE_WHITE_SCREEN -> WhiteScreenSceneActivity::class.java
            SceneIds.SCENE_DYNAMIC_WALLPAPER -> DynamicWallpaperSceneActivity::class.java
            SceneIds.SCENE_GAME_HEAVY -> GameHeavySceneActivity::class.java
            SceneIds.SCENE_FRONT_CAMERA -> FrontCameraSceneActivity::class.java
            SceneIds.SCENE_REAR_CAMERA -> RearCameraSceneActivity::class.java
            else -> null
        }

        if (target == null) {
            ToastUtil.show(context, "未知场景: ${sceneId ?: "null"}")
            return
        }

        val intent = Intent(context, target).apply {
            // Keep duration extra for backward compatibility, but scenes ignore timeout now.
            putExtra(SceneIds.EXTRA_DURATION, duration)
            if (style != null) {
                putExtra(SceneIds.EXTRA_STYLE, style)
            }
            putExtra(SceneIds.EXTRA_PARTICLES, particles)
            putExtra(SceneIds.EXTRA_CPU_THREADS, cpuThreads)
            putExtra(SceneIds.EXTRA_AUDIO, audio)
            putExtra(SceneIds.EXTRA_VIBRATION, vibration)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
