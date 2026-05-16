package com.yunfei.autotestscene.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat

fun Context.registerNotExportedReceiver(
    receiver: BroadcastReceiver,
    filter: IntentFilter
) {
    ContextCompat.registerReceiver(
        this,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
}
