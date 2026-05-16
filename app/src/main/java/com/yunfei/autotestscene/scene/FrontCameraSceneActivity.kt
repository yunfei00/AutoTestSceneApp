package com.yunfei.autotestscene.scene

import androidx.camera.core.CameraSelector

class FrontCameraSceneActivity : BaseCameraSceneActivity() {
    override val sceneTitle: String = "????"
    override val lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    override val showOverlayInfo: Boolean = false
}
