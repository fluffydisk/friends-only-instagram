package dev.friendsonly.installdiagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = DiagnosticStore(context)
        if (intent.action != Installer.ACTION_STATUS ||
            intent.data?.toString() != store.prefs.getString("callback_uri", null) ||
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != store.prefs.getInt("session", -2)
        ) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        store.setResult(InstallResult(
            status,
            if (intent.hasExtra(EXTRA_LEGACY_STATUS)) {
                intent.getIntExtra(EXTRA_LEGACY_STATUS, 0)
            } else null,
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty(),
            intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME),
            if (intent.hasExtra(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON)) {
                intent.getIntExtra(EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, 0)
            } else null,
            if (intent.hasExtra(EXTRA_DEVELOPER_VERIFICATION_LITE_PERFORMED)) {
                intent.getBooleanExtra(EXTRA_DEVELOPER_VERIFICATION_LITE_PERFORMED, false)
            } else null,
        ))
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            Installer.confirmationIntent = confirmation
            confirmation?.let {
                try {
                    context.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: RuntimeException) {
                    // Keep the pending result and allow the foreground activity to open it.
                }
            }
        } else {
            Installer.confirmationIntent = null
            store.prefs.edit().remove("callback_uri").remove("session").apply()
        }
    }

    companion object {
        // AOSP adds this diagnostic extra; it is not a public SDK constant and may be absent.
        private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
        // Public Android 36.1+ extras, read by documented wire name when present.
        private const val EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON =
            "android.content.pm.extra.DEVELOPER_VERIFICATION_FAILURE_REASON"
        private const val EXTRA_DEVELOPER_VERIFICATION_LITE_PERFORMED =
            "android.content.pm.extra.DEVELOPER_VERIFICATION_LITE_PERFORMED"
    }
}
