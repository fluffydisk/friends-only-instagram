package dev.friendsonly.installdiagnostics

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object Installer {
    const val ACTION_STATUS = "dev.friendsonly.installdiagnostics.INSTALL_STATUS"
    const val MAX_BYTES = 1024L * 1024L * 1024L
    private val executor = Executors.newSingleThreadExecutor()
    private val cancellationQueued = AtomicBoolean(false)
    @Volatile var foreground = false
    private var confirmation: Pair<String, Intent>? = null

    fun hasConfirmation(context: Context): Boolean = synchronized(DiagnosticStore.lock) {
        val store = DiagnosticStore(context)
        store.state().canConfirm && confirmation?.first?.let(store::owns) == true
    }

    fun offerConfirmation(context: Context, callback: String, intent: Intent?) = synchronized(DiagnosticStore.lock) {
        val store = DiagnosticStore(context)
        if (intent != null && store.owns(callback) && store.state().canConfirm && confirmation == null) {
            confirmation = callback to Intent(intent)
        }
    }

    fun clearConfirmation() = synchronized(DiagnosticStore.lock) { confirmation = null }

    /** Auto and manual paths share one persisted claim and consume the same in-memory Intent. */
    fun launchConfirmation(context: Context): Boolean {
        val launch = synchronized(DiagnosticStore.lock) {
            val store = DiagnosticStore(context)
            val saved = confirmation ?: return false
            if (!store.owns(saved.first) || !store.claimConfirmation()) return false
            confirmation = null
            saved.first to Intent(saved.second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(launch.second)
        } catch (_: RuntimeException) {
            // A launch attempt is consumed even if Android rejects it; cancellation remains available.
            DiagnosticStore(context).confirmationFailed(launch.first)
        }
        return true
    }

    fun install(context: Context, uri: Uri, declaredSize: Long) {
        val app = context.applicationContext
        val store = DiagnosticStore(app)
        val callbackUri = store.begin() ?: return
        clearConfirmation()
        executor.execute {
            val installer = app.packageManager.packageInstaller
            var sessionId: Int? = null
            var committed = false
            var failure: Exception? = null
            try {
                if (!store.acceptingWork(callbackUri)) throw IOException("Installation cancelled")
                if (declaredSize == 0L || declaredSize > MAX_BYTES) throw IOException("APK size must be 1 byte to 1 GiB")
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName("com.instagram.android")
                    if (declaredSize > 0) setSize(declaredSize)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val createdId = installer.createSession(params)
                sessionId = createdId
                if (!store.attachSession(callbackUri, createdId)) throw IOException("Installation cancelled")
                installer.openSession(createdId).use { session ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    val source = app.contentResolver.openInputStream(uri) ?: throw IOException("APK could not be opened")
                    source.use { input ->
                        session.openWrite("base.apk", 0L, if (declaredSize > 0) declaredSize else -1L).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var lastReported = 0L
                            while (true) {
                                if (!store.acceptingWork(callbackUri)) throw IOException("Installation cancelled")
                                val length = input.read(buffer)
                                if (length == -1) break
                                count += length
                                if (count > MAX_BYTES) throw IOException("APK exceeds the 1 GiB limit")
                                output.write(buffer, 0, length)
                                digest.update(buffer, 0, length)
                                if (count - lastReported >= 4L * 1024L * 1024L) {
                                    store.copied(callbackUri, count)
                                    lastReported = count
                                }
                            }
                            if (count == 0L) throw IOException("APK is empty")
                            session.fsync(output)
                        }
                    }
                    // Both source and session write stream are closed before commit.
                    store.copied(callbackUri, count, digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
                    val callback = Intent(app, InstallStatusReceiver::class.java)
                        .setAction(ACTION_STATUS).setData(Uri.parse(callbackUri))
                    val pending = PendingIntent.getBroadcast(
                        app, createdId, callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
                    )
                    // Persist ownership/phase before commit: Android may deliver a callback immediately.
                    if (!store.beforeCommit(callbackUri)) throw IOException("Could not persist the installation request")
                    session.commit(pending.intentSender)
                    committed = true
                }
            } catch (error: Exception) {
                failure = error
            } finally {
                if (!committed) {
                    var abandonFailure: RuntimeException? = null
                    sessionId?.let {
                        try { installer.abandonSession(it) } catch (error: RuntimeException) { abandonFailure = error }
                    }
                    // Do not permit another attempt until the previous owned session was abandoned.
                    if (abandonFailure != null) {
                        store.cancellationFailed(callbackUri, abandonFailure!!)
                    } else {
                        failure?.let { store.setLocalError(callbackUri, it) }
                    }
                }
            }
        }
    }

    fun cancel(context: Context) {
        if (!cancellationQueued.compareAndSet(false, true)) return
        val app = context.applicationContext
        val store = DiagnosticStore(app)
        val callback = store.requestCancel()
        if (callback == null) { cancellationQueued.set(false); return }
        clearConfirmation()
        // Serialized after any current copy/commit; the worker observes CANCELLING between reads.
        executor.execute {
            try {
                if (!store.owns(callback)) return@execute
                val installer = app.packageManager.packageInstaller
                val sessions = installer.mySessions
                var sessionId = store.state().sessionId
                if (sessionId == null && sessions.isNotEmpty()) {
                    // Recover a process death between createSession and persisting its returned ID.
                    // Do not guess among several sessions or touch another installer's sessions.
                    val orphan = sessions.singleOrNull() ?: throw IllegalStateException("Ambiguous owned sessions")
                    if (!store.adoptForCancellation(callback, orphan.sessionId)) return@execute
                    sessionId = orphan.sessionId
                }
                val targetSession = sessionId
                if (targetSession != null && sessions.any { it.sessionId == targetSession }) {
                    installer.abandonSession(targetSession)
                }
                store.finishCancelled(callback)
            } catch (error: RuntimeException) {
                store.cancellationFailed(callback, error)
            } finally {
                cancellationQueued.set(false)
            }
        }
    }
}
