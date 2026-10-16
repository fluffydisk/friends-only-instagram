package dev.friendsonly.installdiagnostics

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

internal class DiagnosticStore(private val context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("install-diagnostic", Context.MODE_PRIVATE)

    fun setResult(result: InstallResult) {
        prefs.edit().putString("report", result.report(deviceDescription()))
            .putInt("status", result.status).putBoolean("working", false).apply()
    }

    fun setLocalError(error: Throwable) {
        val report = "Friends Only installation diagnostic v1\n${deviceDescription()}\n" +
            "Local request failure: ${error.javaClass.simpleName}\n" +
            "Android message: ${error.message.orEmpty().take(8192)}"
        prefs.edit().putString("report", report).putInt("status", Int.MIN_VALUE)
            .putBoolean("working", false).apply()
    }

    companion object {
        private fun deviceDescription(): String =
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    }

    private fun deviceDescription(): String = buildString {
        appendLine(Companion.deviceDescription())
        try {
            appendLine("Free internal bytes: ${StatFs(Environment.getDataDirectory().path).availableBytes}")
        } catch (_: RuntimeException) { appendLine("Free internal bytes: unavailable") }
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo("com.instagram.android",
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
            val app = info.applicationInfo
            append("Instagram record in current profile: present; installed=${app != null && app.flags and ApplicationInfo.FLAG_INSTALLED != 0}; ")
            append("system=${app != null && app.flags and ApplicationInfo.FLAG_SYSTEM != 0}; ")
            append("updatedSystem=${app != null && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0}; ")
            append("enabled=${app?.enabled}")
        } catch (_: PackageManager.NameNotFoundException) {
            append("Instagram record in current profile: absent")
        } catch (_: RuntimeException) {
            append("Instagram record in current profile: unavailable")
        }
    }
}
