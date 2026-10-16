package dev.friendsonly.installdiagnostics

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object Installer {
    const val ACTION_STATUS = "dev.friendsonly.installdiagnostics.INSTALL_STATUS"
    const val MAX_BYTES = 1024L * 1024L * 1024L
    private val executor = Executors.newSingleThreadExecutor()
    val busy = AtomicBoolean(false)
    @Volatile var confirmationIntent: Intent? = null

    fun install(context: Context, uri: Uri, declaredSize: Long) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        val store = DiagnosticStore(app)
        store.prefs.edit().putBoolean("working", true).putBoolean("copying", true).putLong("copied", 0L)
            .remove("report").remove("status").apply()
        executor.execute {
            val installer = app.packageManager.packageInstaller
            var sessionId: Int? = null
            var committed = false
            try {
                if (declaredSize == 0L || declaredSize > MAX_BYTES) throw IOException("APK size must be 1 byte to 1 GiB")
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName("com.instagram.android")
                    if (declaredSize > 0) setSize(declaredSize)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val source = app.contentResolver.openInputStream(uri) ?: throw IOException("APK could not be opened")
                    source.use { input ->
                        session.openWrite("base.apk", 0L, if (declaredSize > 0) declaredSize else -1L).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var count = 0L
                            var lastReported = 0L
                            while (true) {
                                val length = input.read(buffer)
                                if (length == -1) break
                                count += length
                                if (count > MAX_BYTES) throw IOException("APK exceeds the 1 GiB limit")
                                output.write(buffer, 0, length)
                                if (count - lastReported >= 4L * 1024L * 1024L) {
                                    store.prefs.edit().putLong("copied", count).apply()
                                    lastReported = count
                                }
                            }
                            if (count == 0L) throw IOException("APK is empty")
                            session.fsync(output)
                            store.prefs.edit().putLong("copied", count).apply()
                        }
                    }
                    val callbackUri = "friendsonly-install://status/${UUID.randomUUID()}"
                    // Persist before commit: Android can deliver the result immediately.
                    if (!store.prefs.edit().putString("callback_uri", callbackUri).putInt("session", sessionId).commit()) {
                        throw IOException("Could not persist the installation callback")
                    }
                    val callback = Intent(app, InstallStatusReceiver::class.java)
                        .setAction(ACTION_STATUS).setData(Uri.parse(callbackUri))
                    val pending = PendingIntent.getBroadcast(
                        app, sessionId, callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
                    )
                    session.commit(pending.intentSender)
                    committed = true
                }
            } catch (error: Exception) {
                store.setLocalError(error)
            } finally {
                if (!committed) {
                    sessionId?.let { try { installer.abandonSession(it) } catch (_: RuntimeException) { } }
                    store.prefs.edit().remove("callback_uri").remove("session").apply()
                }
                busy.set(false)
                // Notify a resumed activity even when no final status has arrived yet.
                store.prefs.edit().putBoolean("copying", false).apply()
            }
        }
    }
}
