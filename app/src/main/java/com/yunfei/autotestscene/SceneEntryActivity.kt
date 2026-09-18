package com.yunfei.autotestscene

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.yunfei.autotestscene.scene.SceneIds
import com.yunfei.autotestscene.scene.SceneRouter
import com.yunfei.autotestscene.scene.SceneWatchdog

class SceneEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scene = intent.getStringExtra(SceneIds.EXTRA_SCENE)
        val duration = intent.getIntExtra(SceneIds.EXTRA_DURATION, 0)
        val style = intent.getStringExtra(SceneIds.EXTRA_STYLE)
        val particles = intent.getIntExtra(SceneIds.EXTRA_PARTICLES, 250)
        val cpuThreads = intent.getIntExtra(SceneIds.EXTRA_CPU_THREADS, 2)
        val audio = intent.getBooleanExtra(SceneIds.EXTRA_AUDIO, false)
        val vibration = intent.getBooleanExtra(SceneIds.EXTRA_VIBRATION, false)
        val started = SceneRouter.openScene(
            context = this,
            sceneId = scene,
            duration = duration,
            style = style,
            particles = particles,
            cpuThreads = cpuThreads,
            audio = audio,
            vibration = vibration
        )
        if (started) {
            SceneWatchdog.arm(applicationContext, duration)
        }
        finish()
    }
}
