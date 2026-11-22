package com.yunfei.autotestscene.scene

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal val CONTROLLED_SCENES = listOf(
    "video", "music", "game_heavy", "front_camera", "rear_camera",
    "motor", "flashlight", "recorder", "white_screen",
)

internal fun validateScene(scene: String?, version: Int, duration: Int, particles: Int, workers: Int): String? =
    when {
        version != 1 -> "UNSUPPORTED_PROTOCOL"
        scene !in CONTROLLED_SCENES -> "UNSUPPORTED_SCENE"
        duration !in 1..86400 || particles !in 20..1000 || workers !in 1..8 -> "INVALID_PARAMETERS"
        else -> null
    }

internal data class OperatorSceneState(val scene: String? = null, val status: String = "IDLE", val reason: String? = null)

object SceneControl {
    const val INSTANCE = "scene_instance_id"
    internal const val OPERATOR_SCENE = "operator_scene"
    private val handler = Handler(Looper.getMainLooper())
    private val instances = LinkedHashMap<String, Instance>()
    private var current: Instance? = null
    private var manualActivity: ControlledSceneActivity? = null
    private val operator = MutableStateFlow(OperatorSceneState())
    internal val operatorState = operator.asStateFlow()

    private class Instance(val id: String, val scene: String) {
        var state = "STARTING"
        var reason: String? = null
        var activity: ControlledSceneActivity? = null
        var elapsed = SystemClock.elapsedRealtimeNanos()

        fun transition(next: String, error: String? = null) {
            state = next
            reason = error
            elapsed = SystemClock.elapsedRealtimeNanos()
            if (this === current) operator.value = OperatorSceneState(scene, next, error)
        }

        fun json() = JSONObject().put("scene_instance_id", id).put("scene_id", scene)
            .put("state", state).put("reason", reason).put("elapsed_realtime_nanos", elapsed)
    }

    fun start(id: String, scene: String?, version: Int, duration: Int, particles: Int, workers: Int): Boolean {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id || id in instances) return false
        val instance = Instance(id, scene.orEmpty())
        if (instances.size >= 128) instances.remove(instances.keys.first())
        instances[id] = instance
        val error = validateScene(scene, version, duration, particles, workers)
            ?: if (current != null || manualActivity != null || operator.value.status == "STARTING") "SCENE_BUSY" else null
        if (error != null) {
            instance.transition("ERROR", error)
            return false
        }
        current = instance
        operator.value = OperatorSceneState(instance.scene, instance.state)
        handler.postDelayed({
            if (current === instance && instance.state == "STARTING") stop(instance, "READY_TIMEOUT")
        }, minOf(30_000L, duration * 1_000L))
        handler.postDelayed({
            if (current === instance) stop(instance, "SCENE_DEADLINE")
        }, duration * 1_000L)
        return true
    }

    fun attach(activity: ControlledSceneActivity) {
        val id = activity.intent.getStringExtra(INSTANCE)
        if (id == null) {
            val scene = activity.intent.getStringExtra(OPERATOR_SCENE) ?: return
            if (current == null && manualActivity == null && operator.value == OperatorSceneState(scene, "STARTING")) {
                manualActivity = activity
            } else {
                activity.finish()
            }
            return
        }
        val instance = current
        if (instance?.id == id && instance.activity == null && instance.state == "STARTING") {
            instance.activity = activity
        } else {
            activity.finish()
        }
    }

    fun ready(activity: ControlledSceneActivity) {
        current?.takeIf { it.activity === activity && it.state == "STARTING" }?.transition("READY")
        if (manualActivity === activity && operator.value.status == "STARTING") {
            operator.value = operator.value.copy(status = "READY")
        }
    }

    fun error(activity: ControlledSceneActivity, reason: String) {
        current?.takeIf { it.activity === activity }?.let { stop(it, reason) }
        if (manualActivity === activity) {
            operator.value = operator.value.copy(status = "ERROR", reason = reason)
            activity.finish()
        }
    }

    fun error(id: String, reason: String) {
        current?.takeIf { it.id == id }?.let { stop(it, reason) }
    }

    fun destroyed(activity: ControlledSceneActivity) {
        if (manualActivity === activity) {
            manualActivity = null
            if (operator.value.status != "ERROR") operator.value = operator.value.copy(status = "STOPPED")
            return
        }
        val instance = current?.takeIf { it.activity === activity } ?: return
        instance.activity = null
        if (instance.state == "STOPPING") instance.transition("STOPPED")
        else if (instance.state != "ERROR") instance.transition("ERROR", "SCENE_EXITED")
        current = null
    }

    internal fun openManual(scene: String, launch: () -> Unit) {
        if (current != null || manualActivity != null || operator.value.status == "STARTING") return
        val starting = OperatorSceneState(scene, "STARTING")
        operator.value = starting
        try {
            launch()
        } catch (_: android.content.ActivityNotFoundException) {
            operator.value = starting.copy(status = "ERROR", reason = "SCENE_UNAVAILABLE")
        }
        handler.postDelayed({
            if (current == null && operator.value === starting) {
                val activity = manualActivity
                if (activity == null) operator.value = starting.copy(status = "ERROR", reason = "READY_TIMEOUT")
                else error(activity, "READY_TIMEOUT")
            }
        }, 30_000L)
    }

    internal fun stopFromOperator() {
        current?.let { stop(it); return }
        val activity = manualActivity
        if (activity != null) {
            operator.value = operator.value.copy(status = "STOPPING")
            activity.finish()
        } else if (operator.value.status == "STARTING") {
            operator.value = operator.value.copy(status = "STOPPED")
        }
    }

    fun status(id: String, stop: Boolean): JSONObject {
        val instance = instances[id] ?: return JSONObject().put("state", "ERROR").put("reason", "UNKNOWN_SCENE")
        if (stop && instance === current) stop(instance)
        return instance.json()
    }

    private fun stop(instance: Instance, error: String? = null) {
        if (instance.state == "ERROR") return
        if (instance.state == "STOPPING") {
            if (error != null && error != "SCENE_BACKGROUND") instance.transition("ERROR", error)
            return
        }
        instance.transition(if (error == null) "STOPPING" else "ERROR", error)
        val activity = instance.activity
        if (activity != null) activity.finish()
        else {
            if (error == null) instance.transition("STOPPED")
            current = null
        }
    }
}

abstract class ControlledSceneActivity : ComponentActivity() {
    protected val isControlledScene: Boolean get() = intent.getStringExtra(SceneControl.INSTANCE) != null
    private var resumed = false
    private var resourceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SceneControl.attach(this)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (resourceReady) SceneControl.ready(this)
    }

    override fun onStop() {
        super.onStop()
        if (isControlledScene) SceneControl.error(this, "SCENE_BACKGROUND")
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    protected fun resourceReady() {
        resourceReady = true
        if (resumed) SceneControl.ready(this)
    }

    protected fun resourceError(reason: String) = SceneControl.error(this, reason)

    override fun onDestroy() {
        SceneControl.destroyed(this)
        super.onDestroy()
    }
}

class SceneControlProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == 2000) { "Android shell required" }
        val task = FutureTask {
            when (method) {
                "capabilities" -> JSONObject().put("protocol_version", 1)
                    .put("app_version", context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName)
                    .put("operations", JSONArray(listOf("start", "status", "stop")))
                    .put("scene_ids", JSONArray(CONTROLLED_SCENES))
                "status", "stop" -> SceneControl.status(arg.orEmpty(), method == "stop")
                else -> JSONObject().put("state", "ERROR").put("reason", "UNKNOWN_METHOD")
            }
        }
        Handler(Looper.getMainLooper()).post(task)
        val result = task.get(8, TimeUnit.SECONDS)
        return Bundle().apply {
            putString("reply", Base64.encodeToString(result.toString().encodeToByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
