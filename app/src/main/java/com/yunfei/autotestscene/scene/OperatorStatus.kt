package com.yunfei.autotestscene.scene

internal fun operatorStatus(status: String): String = when (status) {
    "Ready", "Running", "Playing", "Recording" -> "运行中"
    "Starting", "Preparing" -> "正在启动"
    "Stopped", "Idle" -> "已停止"
    "Error" -> "运行异常"
    "Unsupported" -> "设备不支持"
    else -> status
}
