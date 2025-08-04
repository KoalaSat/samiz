package com.koalasat.samiz.util

import android.content.Context
import android.content.pm.PackageManager

class AppSystem {
    companion object {
        fun getAppVersion(context: Context): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: ""
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown"
            }
        }
    }
}
