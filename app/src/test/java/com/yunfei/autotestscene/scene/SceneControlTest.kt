package com.yunfei.autotestscene.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneControlTest {
    @Test
    fun acceptsCommonTestScenesInVersionOne() {
        for (scene in listOf("motor", "flashlight", "recorder", "white_screen")) {
            assertNull(validateScene(scene, 1, 60, 250, 2))
        }
    }

    @Test
    fun validatesVersionAndWorkloadBeforeLaunch() {
        assertNull(validateScene("game_heavy", 1, 120, 250, 2))
        assertEquals("UNSUPPORTED_PROTOCOL", validateScene("video", 2, 120, 250, 2))
        assertEquals("UNSUPPORTED_SCENE", validateScene("game_2d", 1, 120, 250, 2))
        assertEquals("INVALID_PARAMETERS", validateScene("game_heavy", 1, 120, 1001, 2))
        assertEquals("INVALID_PARAMETERS", validateScene("game_heavy", 1, 120, 250, 0))
        assertEquals("INVALID_PARAMETERS", validateScene("video", 1, 0, 250, 2))
    }
}
