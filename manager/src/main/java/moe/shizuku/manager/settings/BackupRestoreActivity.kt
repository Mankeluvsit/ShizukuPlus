package moe.shizuku.manager.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityBackupRestoreBinding
import org.json.JSONArray
import org.json.JSONObject

class BackupRestoreActivity : AppBarActivity() {

    companion object {
        private const val BACKUP_VERSION = 1
        private const val APP_MANAGEMENT_PREFS = "app_management_prefs"
        private const val MAX_IMPORT_LENGTH = 512 * 1024
        private const val KEY_VERSION = "version"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_APP_MANAGEMENT = "app_management"
    }

    private lateinit var binding: ActivityBackupRestoreBinding

    override fun getLayoutId() = R.layout.activity_backup_restore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupRestoreBinding.bind(rootView)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.exportPreview.text = buildExportJson()
        binding.btnCopyBackup.setOnClickListener { copyBackup() }
        binding.btnShareBackup.setOnClickListener { shareBackup() }
        binding.btnRefreshPreview.setOnClickListener { refreshPreview() }
        binding.btnImportBackup.setOnClickListener { showImportDialog() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun refreshPreview() {
        binding.exportPreview.text = buildExportJson()
    }

    private fun buildExportJson(): String {
        val root = JSONObject()
        root.put(KEY_VERSION, BACKUP_VERSION)
        root.put("source", packageName)
        root.put("exported_at", System.currentTimeMillis())
        root.put(KEY_SETTINGS, prefsToJson(ShizukuSettings.getPreferences()))
        root.put(KEY_APP_MANAGEMENT, prefsToJson(getSharedPreferences(APP_MANAGEMENT_PREFS, Context.MODE_PRIVATE)))
        return root.toString(2)
    }

    private fun prefsToJson(prefs: android.content.SharedPreferences): JSONObject {
        val json = JSONObject()
        prefs.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                is Set<*> -> json.put(key, JSONArray(value.toList()))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun copyBackup() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.backup_restore_title), buildExportJson()))
        Toast.makeText(this, R.string.termux_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareBackup() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, buildExportJson())
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.common_share)))
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            minLines = 8
            maxLines = 20
            hint = getString(R.string.backup_restore_import_hint)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_restore_import_title)
            .setView(input)
            .setPositiveButton(R.string.backup_restore_import_button) { _, _ ->
                importBackup(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importBackup(rawJson: String) {
        if (rawJson.isBlank()) {
            Toast.makeText(this, R.string.backup_restore_import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (rawJson.length > MAX_IMPORT_LENGTH) {
            Toast.makeText(this, getString(R.string.backup_restore_import_failed, "backup is too large"), Toast.LENGTH_LONG).show()
            return
        }

        try {
            val root = JSONObject(rawJson)
            val version = root.optInt(KEY_VERSION, -1)
            if (version != BACKUP_VERSION) {
                throw IllegalArgumentException("unsupported backup version: $version")
            }
            val settings = root.optJSONObject(KEY_SETTINGS)
                ?: throw IllegalArgumentException("missing settings object")
            val appManagement = root.optJSONObject(KEY_APP_MANAGEMENT)
                ?: throw IllegalArgumentException("missing app_management object")

            applyPrefsFromJson(ShizukuSettings.getPreferences(), settings)
            applyPrefsFromJson(getSharedPreferences(APP_MANAGEMENT_PREFS, Context.MODE_PRIVATE), appManagement)
            ShizukuSettings.syncAllPlusFeaturesToServer()
            Toast.makeText(this, R.string.backup_restore_import_success, Toast.LENGTH_LONG).show()
            refreshPreview()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_restore_import_failed, e.message ?: "invalid JSON"), Toast.LENGTH_LONG).show()
        }
    }

    private fun applyPrefsFromJson(
        prefs: android.content.SharedPreferences,
        json: JSONObject?
    ) {
        if (json == null) return
        val editor = prefs.edit()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is JSONArray -> {
                    val set = linkedSetOf<String>()
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item !is String) {
                            throw IllegalArgumentException("non-string value in set for $key")
                        }
                        set += item
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
    }
}
