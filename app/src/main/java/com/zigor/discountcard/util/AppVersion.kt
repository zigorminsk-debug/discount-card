package com.zigor.discountcard.util

import android.content.Context
import android.os.Build

data class AppVersion(val name: String, val code: Long)

fun Context.appVersion(): AppVersion = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    AppVersion(info.versionName ?: "—", code)
}.getOrDefault(AppVersion("—", 0))
