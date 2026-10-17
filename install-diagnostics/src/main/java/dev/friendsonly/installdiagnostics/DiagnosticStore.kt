package dev.friendsonly.installdiagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.UUID

internal class DiagnosticStore(private val context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("install-diagnostic", Context.MODE_PRIVATE)

    fun state(): InstallSessionState = synchronized(lock) {
        val phase = prefs.getString("phase", null)?.let {
            try { InstallSessionState.Phase.valueOf(it) } catch (_: IllegalArgumentException) { null }
        } ?: if (prefs.contains("session") || prefs.getBoolean("working", false)) {
            InstallSessionState.Phase.WAITING_RESULT
        } else InstallSessionState.Phase.IDLE
        InstallSessionState(phase, if (prefs.contains("session")) prefs.getInt("session", -1).takeIf { it >= 0 } else null,
            prefs.getInt("confirmation_launch_count", 0))
    }

    fun begin(): String? = synchronized(lock) {
        val next = state().begin() ?: return@synchronized null
        val callback = "friendsonly-install://status/${UUID.randomUUID()}"
        val editor = prefs.edit().putString("callback_uri", callback).remove("status")
        if (save(next, editor, initialReport(), 0L, "", -1)) callback else null
    }

    fun owns(callback: String): Boolean = synchronized(lock) {
        state().active && prefs.getString("callback_uri", null) == callback
    }

    fun acceptingWork(callback: String): Boolean = synchronized(lock) {
        owns(callback) && state().phase != InstallSessionState.Phase.CANCELLING
    }

    fun attachSession(callback: String, sessionId: Int): Boolean = synchronized(lock) {
        if (!owns(callback)) return@synchronized false
        val next = state().attachSession(sessionId) ?: return@synchronized false
        save(next, traceSession = sessionId)
    }

    fun copied(callback: String, bytes: Long, sha256: String? = null) = synchronized(lock) {
        if (acceptingWork(callback)) save(state(), copiedBytes = bytes,
            sha256 = sha256 ?: prefs.getString("source_sha256", "").orEmpty())
    }

    fun beforeCommit(callback: String): Boolean = synchronized(lock) {
        if (!acceptingWork(callback)) return@synchronized false
        save(state().committed())
    }

    fun acceptsCallback(callback: String?, sessionId: Int): Boolean = synchronized(lock) {
        callback != null && owns(callback) && state().sessionId == sessionId
    }

    fun setResult(callback: String, result: InstallResult): Boolean = synchronized(lock) {
        if (!owns(callback)) return@synchronized false
        val old = state()
        val pending = result.status == -1
        // A real Android terminal result wins a cancellation race; late consent must not reopen it.
        if (pending && old.phase == InstallSessionState.Phase.CANCELLING) return@synchronized false
        val next = if (pending) old.pending() else old.finish()
        val editor = prefs.edit().putInt("status", result.status)
        if (!pending) editor.remove("callback_uri")
        save(next, editor, result.report(deviceDescription()))
    }

    fun claimConfirmation(): Boolean = synchronized(lock) {
        val next = state().claimConfirmation() ?: return@synchronized false
        save(next)
    }

    fun requestCancel(): String? = synchronized(lock) {
        val old = state()
        if (!old.active) return@synchronized null
        val callback = prefs.getString("callback_uri", null) ?: "friendsonly-install://status/${UUID.randomUUID()}"
        if (save(old.cancelling(), prefs.edit().putString("callback_uri", callback))) callback else null
    }

    fun finishCancelled(callback: String) = synchronized(lock) {
        if (owns(callback)) {
            save(state().finish(), prefs.edit().remove("callback_uri").putInt("status", 3),
                initialReport() + "\nLocal action: current installation session cancelled by user.")
        }
    }

    fun adoptForCancellation(callback: String, sessionId: Int): Boolean = synchronized(lock) {
        if (!owns(callback) || state().phase != InstallSessionState.Phase.CANCELLING) return@synchronized false
        save(state().copy(sessionId = sessionId), traceSession = sessionId)
    }

    fun setLocalError(callback: String, error: Throwable) = synchronized(lock) {
        if (!owns(callback) || state().phase == InstallSessionState.Phase.CANCELLING) return@synchronized
        save(state().finish(), prefs.edit().remove("callback_uri").putInt("status", Int.MIN_VALUE),
            initialReport() + "\nLocal request failure: ${error.javaClass.simpleName}")
    }

    fun confirmationFailed(callback: String) = synchronized(lock) {
        if (!owns(callback)) return@synchronized
        save(state(), baseReport = prefs.getString("base_report", initialReport()).orEmpty() +
            "\nConfirmation launch failed locally. Cancel this session before another test.")
    }

    fun cancellationFailed(callback: String, error: Throwable) = synchronized(lock) {
        if (!owns(callback)) return@synchronized
        save(state().cancelling(), baseReport = initialReport() +
            "\nCould not abandon the current session: ${error.javaClass.simpleName}. Use Cancel current test to retry cancellation.")
    }

    private fun save(
        state: InstallSessionState,
        editor: SharedPreferences.Editor = prefs.edit(),
        baseReport: String = prefs.getString("base_report", null) ?: initialReport(),
        copiedBytes: Long = prefs.getLong("copied", 0L),
        sha256: String = prefs.getString("source_sha256", "").orEmpty(),
        traceSession: Int = prefs.getInt("trace_session", -1),
    ): Boolean {
        editor.putString("phase", state.phase.name).putBoolean("working", state.active)
            .putBoolean("copying", state.copying).putInt("confirmation_launch_count", state.confirmationLaunchCount)
            .putLong("copied", copiedBytes).putString("source_sha256", sha256).putInt("trace_session", traceSession)
            .putString("base_report", baseReport)
        if (state.sessionId == null) editor.remove("session") else editor.putInt("session", state.sessionId)
        val trace = "\n\nLocal trace:\nSession ID: ${traceSession.takeIf { it >= 0 } ?: "not created"}" +
            "\nPhase: ${state.phase}\nCopied bytes: $copiedBytes" +
            "\nSource SHA-256: ${sha256.ifBlank { "not complete" }}" +
            "\nConfirmation launch count: ${state.confirmationLaunchCount}"
        return editor.putString("report", baseReport + trace).commit()
    }

    private fun initialReport(): String =
        "Friends Only installation diagnostic v2\n${deviceDescription()}\nPackage: com.instagram.android"

    companion object { val lock = Any() }

    private fun deviceDescription(): String = buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
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
