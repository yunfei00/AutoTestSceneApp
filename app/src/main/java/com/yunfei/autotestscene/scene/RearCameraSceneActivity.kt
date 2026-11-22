package com.yunfei.autotestscene.scene

import androidx.camera.core.CameraSelector

class RearCameraSceneActivity : BaseCameraSceneActivity() {
    override val sceneTitle: String = "后置摄像头"
    override val lensFacing: Int = CameraSelector.LENS_FACING_BACK
    override val showOverlayInfo: Boolean = false
}
