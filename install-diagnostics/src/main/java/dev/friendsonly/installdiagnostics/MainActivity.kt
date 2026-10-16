package dev.friendsonly.installdiagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: DiagnosticStore
    private lateinit var fileText: TextView
    private lateinit var resultText: TextView
    private lateinit var stateText: TextView
    private lateinit var install: Button
    private lateinit var choose: Button
    private lateinit var copy: Button
    private lateinit var share: Button
    private var selected: Uri? = null
    private var selectedSize = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DiagnosticStore(this)
        buildLayout()
        store.prefs.getString("selected_uri", null)?.let { selected = Uri.parse(it) }
        selectedSize = store.prefs.getLong("selected_size", -1L)
        fileText.text = store.prefs.getString("selected_description", null) ?: getString(R.string.no_file)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = refresh()

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(20) + bars.left, dp(20) + bars.top, dp(20) + bars.right, dp(20) + bars.bottom)
                WindowInsets.CONSUMED
            }
        } else root.fitsSystemWindows = true
        root.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 24f })
        root.addView(TextView(this).apply { text = getString(R.string.explanation); textSize = 16f; setPadding(0, dp(16), 0, dp(16)) })
        fileText = TextView(this).also { it.textSize = 15f; root.addView(it) }
        choose = button(root, R.string.choose_apk) { chooseApk() }
        install = button(root, R.string.install_apk) { requestInstall() }
        stateText = TextView(this).also { it.textSize = 15f; root.addView(it) }
        resultText = TextView(this).also { it.setTextIsSelectable(true); it.textSize = 14f; it.setPadding(0, dp(16), 0, dp(16)); root.addView(it) }
        copy = button(root, R.string.copy_result) {
            val report = store.prefs.getString("report", null) ?: return@button
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Installation diagnostic", report))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        share = button(root, R.string.share_result) {
            val report = store.prefs.getString("report", null) ?: return@button
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), getString(R.string.share_title)))
        }
        root.addView(TextView(this).apply { text = getString(R.string.privacy); setPadding(0, dp(16), 0, 0) })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun button(root: LinearLayout, text: Int, action: () -> Unit): Button = Button(this).also {
        it.setText(text)
        it.setOnClickListener { action() }
        root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun chooseApk() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQUEST_FILE)
        } catch (_: RuntimeException) { toast(R.string.file_unavailable) }
    }

    @Deprecated("Uses framework result API to avoid extra dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (uri.scheme != "content") throw IllegalArgumentException("A document provider is required")
            var name: String? = null
            var size = -1L
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
                }
            }
            if (name?.endsWith(".apk", ignoreCase = true) != true) { toast(R.string.selected_invalid); return }
            if (size == 0L || size > Installer.MAX_BYTES) { toast(R.string.file_too_large); return }
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Providers may grant only temporary access. The current attempt can still work.
            }
            selected?.takeIf { it != uri }?.let { previous ->
                try { contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            }
            selected = uri
            selectedSize = size
            val description = getString(R.string.file_details, name!!.take(160), if (size > 0) getString(R.string.size_bytes, size) else getString(R.string.size_unknown))
            fileText.text = description
            store.prefs.edit().putString("selected_uri", uri.toString()).putLong("selected_size", size).putString("selected_description", description).apply()
            refresh()
        } catch (_: Exception) { toast(R.string.file_unavailable) }
    }

    private fun requestInstall() {
        if (Installer.busy.get()) return
        Installer.confirmationIntent?.let {
            try { startActivity(Intent(it)); return } catch (_: RuntimeException) {
                Installer.confirmationIntent = null
                toast(R.string.confirmation_unavailable)
                return
            }
        }
        if (!packageManager.canRequestPackageInstalls()) {
            toast(R.string.source_explanation)
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: RuntimeException) { toast(R.string.permission_unavailable) }
            return
        }
        val uri = selected ?: return
        Installer.install(applicationContext, uri, selectedSize)
        refresh()
    }

    private fun refresh() {
        if (!::install.isInitialized) return
        val report = store.prefs.getString("report", null)
        resultText.text = report ?: getString(R.string.no_result)
        copy.isEnabled = report != null
        share.isEnabled = report != null
        val busy = Installer.busy.get()
        choose.isEnabled = !busy
        install.isEnabled = !busy && (selected != null || Installer.confirmationIntent != null)
        install.setText(when {
            Installer.confirmationIntent != null -> R.string.confirm_installation
            !packageManager.canRequestPackageInstalls() -> R.string.allow_source
            else -> R.string.install_apk
        })
        stateText.visibility = View.VISIBLE
        stateText.text = when {
            busy -> getString(R.string.working, store.prefs.getLong("copied", 0L) / (1024L * 1024L))
            store.prefs.getBoolean("working", false) -> getString(R.string.waiting)
            store.prefs.getInt("status", Int.MIN_VALUE) == -1 -> getString(R.string.pending)
            else -> ""
        }
    }

    private fun toast(resource: Int) = Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val REQUEST_FILE = 42 }
}
