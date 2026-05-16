package com.yunfei.autotestscene.util

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun show(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
