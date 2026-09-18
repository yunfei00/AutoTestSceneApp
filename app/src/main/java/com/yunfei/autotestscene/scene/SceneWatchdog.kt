package com.yunfei.autotestscene.scene

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

object SceneWatchdog {
    private const val TAG = "AutoTestSceneApp"
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStop: Runnable? = null

    @Synchronized
    fun arm(context: Context, durationSeconds: Int) {
        cancel()
        if (durationSeconds <= 0) return

        val appContext = context.applicationContext
        val runnable = Runnable {
            Log.i(TAG, "Scene watchdog timeout after ${durationSeconds}s")
            appContext.sendBroadcast(
                Intent(SceneIds.ACTION_STOP).apply {
                    setPackage(appContext.packageName)
                }
            )
        }
        pendingStop = runnable
        handler.postDelayed(runnable, durationSeconds * 1000L)
        Log.i(TAG, "Scene watchdog armed: ${durationSeconds}s")
    }

    @Synchronized
    fun cancel() {
        pendingStop?.let(handler::removeCallbacks)
        pendingStop = null
    }
}
