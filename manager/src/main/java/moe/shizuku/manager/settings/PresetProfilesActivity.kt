package moe.shizuku.manager.settings

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.Keys
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityPresetProfilesBinding

class PresetProfilesActivity : AppBarActivity() {

    private lateinit var binding: ActivityPresetProfilesBinding

    override fun getLayoutId() = R.layout.activity_preset_profiles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetProfilesBinding.bind(rootView)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        binding.btnApplyBalanced.setOnClickListener { applyPreset(Preset.BALANCED) }
        binding.btnApplyTermux.setOnClickListener { applyPreset(Preset.TERMUX_POWER) }
        binding.btnApplyLegacyRoot.setOnClickListener { applyPreset(Preset.LEGACY_ROOT) }
        binding.btnApplyDiagnostics.setOnClickListener { applyPreset(Preset.DIAGNOSTICS) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun applyPreset(preset: Preset) {
        val prefs = ShizukuSettings.getPreferences() ?: return
        prefs.edit().apply {
            when (preset) {
                Preset.BALANCED -> {
                    putBoolean(Keys.KEY_CUSTOM_API_ENABLED, true)
                    putBoolean(Keys.KEY_SHELL_INTERCEPTOR_ENABLED, true)
                    putBoolean(Keys.KEY_ENABLE_ACTIVITY_LOG, true)
                    putBoolean(Keys.KEY_SHOW_TERMINAL_HOME, true)
                    putBoolean(Keys.KEY_SHOW_ACTIVITY_LOG_HOME, true)
                    putBoolean(Keys.KEY_SHOW_START_ADB_HOME, true)
                    putBoolean(Keys.KEY_ADB_PROXY_ENABLED, false)
                    putBoolean(Keys.KEY_SU_BRIDGE_ENABLED, false)
                    putBoolean(Keys.KEY_VECTOR_ENABLED, false)
                    putBoolean(Keys.KEY_EXPERIMENTAL_ROOT_COMPAT, false)
                }
                Preset.TERMUX_POWER -> {
                    putBoolean(Keys.KEY_CUSTOM_API_ENABLED, true)
                    putBoolean(Keys.KEY_SHELL_INTERCEPTOR_ENABLED, true)
                    putBoolean(Keys.KEY_ENABLE_ACTIVITY_LOG, true)
                    putBoolean(Keys.KEY_SHOW_TERMINAL_HOME, true)
                    putBoolean(Keys.KEY_SHOW_ACTIVITY_LOG_HOME, true)
                    putBoolean(Keys.KEY_SHOW_START_ADB_HOME, true)
                    putBoolean(Keys.KEY_ADB_PROXY_ENABLED, true)
                    putBoolean(Keys.KEY_SU_BRIDGE_ENABLED, true)
                    putBoolean(Keys.KEY_ACTIVITY_MANAGER_PLUS_ENABLED, true)
                    putBoolean(Keys.KEY_HIDE_DISABLED_PLUS_FEATURES, false)
                }
                Preset.LEGACY_ROOT -> {
                    putBoolean(Keys.KEY_CUSTOM_API_ENABLED, true)
                    putBoolean(Keys.KEY_ENABLE_ACTIVITY_LOG, true)
                    putBoolean(Keys.KEY_SU_BRIDGE_ENABLED, true)
                    putBoolean(Keys.KEY_EXPERIMENTAL_ROOT_COMPAT, true)
                    putBoolean(Keys.KEY_SPOOF_DEVICE_ENABLED, true)
                    putBoolean(Keys.KEY_SHOW_TERMINAL_HOME, false)
                    putBoolean(Keys.KEY_SHOW_ACTIVITY_LOG_HOME, true)
                    putBoolean(Keys.KEY_SHOW_START_ADB_HOME, false)
                }
                Preset.DIAGNOSTICS -> {
                    putBoolean(Keys.KEY_ENABLE_ACTIVITY_LOG, true)
                    putBoolean(Keys.KEY_SHOW_ACTIVITY_LOG_HOME, true)
                    putBoolean(Keys.KEY_SHOW_TERMINAL_HOME, true)
                    putBoolean(Keys.KEY_SHOW_START_ADB_HOME, true)
                    putBoolean(Keys.KEY_HIDE_DISABLED_PLUS_FEATURES, false)
                    putBoolean(Keys.KEY_VECTOR_ENABLED, false)
                    putBoolean(Keys.KEY_EXPERIMENTAL_ROOT_COMPAT, false)
                }
            }
        }.apply()

        ShizukuSettings.syncAllPlusFeaturesToServer()
        Toast.makeText(this, getString(R.string.preset_profiles_applied, getString(preset.labelRes)), Toast.LENGTH_SHORT).show()
    }

    private enum class Preset(val labelRes: Int) {
        BALANCED(R.string.preset_balanced_title),
        TERMUX_POWER(R.string.preset_termux_title),
        LEGACY_ROOT(R.string.preset_legacy_root_title),
        DIAGNOSTICS(R.string.preset_diagnostics_title)
    }
}
