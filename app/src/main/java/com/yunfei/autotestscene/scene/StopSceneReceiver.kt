package com.yunfei.autotestscene.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yunfei.autotestscene.util.ToastUtil

class StopSceneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SceneIds.ACTION_STOP) return

        SceneWatchdog.cancel()
        context.sendBroadcast(
            Intent(SceneIds.ACTION_STOP_INTERNAL).apply {
                setPackage(context.packageName)
            }
        )
        Log.i("StopSceneReceiver", "External STOP forwarded to active scene")
        ToastUtil.show(context, "已发送停止场景广播")
    }
}
