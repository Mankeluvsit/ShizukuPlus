package moe.shizuku.manager.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.databinding.ActivityTermuxIntegrationBinding
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.utils.ActivityLogManager
import moe.shizuku.manager.utils.ShizukuStateMachine

class TermuxIntegrationActivity : AppBarActivity() {

    private lateinit var binding: ActivityTermuxIntegrationBinding
    private var detectedApp: TermuxApp? = null

    override fun getLayoutId() = R.layout.activity_termux_integration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermuxIntegrationBinding.bind(rootView)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        binding.btnGrantTermux.setOnClickListener { grantTermuxAccess() }
        binding.btnOpenTermux.setOnClickListener { openTermux() }
        binding.btnCopyBootstrap.setOnClickListener { copyBootstrapCommand() }
        binding.btnCopyVerify.setOnClickListener { copyVerificationCommand() }
        binding.btnShellTutorial.setOnClickListener {
            startActivity(Intent(this, ShellTutorialActivity::class.java))
        }
        binding.btnRefreshStatus.setOnClickListener { refreshStatus() }

        refreshStatus()
    }

    private fun refreshStatus() {
        detectedApp = detectTermuxApp()
        val app = detectedApp
        val exportDir = ShizukuSettings.getExportDirUri()
        val serviceRunning = ShizukuStateMachine.isRunning()

        if (app == null) {
            binding.statusTitle.text = getString(R.string.termux_not_found_title)
            binding.statusSummary.text = getString(R.string.termux_not_found_summary)
            binding.detectedPackage.text = getString(R.string.termux_detected_package_none)
            binding.authorizationStatus.text = getString(R.string.termux_authorization_unknown)
            binding.exportStatus.text = getString(R.string.termux_export_status_missing)
            binding.serverStatus.text = getString(R.string.termux_service_stopped)
            binding.bootstrapCommand.text = getString(R.string.termux_bootstrap_missing)
            binding.verificationCommand.text = getString(R.string.termux_verification_missing)
            binding.btnGrantTermux.isEnabled = false
            binding.btnOpenTermux.isEnabled = false
            binding.btnCopyBootstrap.isEnabled = false
            binding.btnCopyVerify.isEnabled = false
            binding.warningCard.isVisible = true
            binding.warningText.text = getString(R.string.termux_not_found_summary)
            return
        }

        val isAuthorized = runCatching { AuthorizationManager.granted(app.packageName, app.uid) }.getOrDefault(false)
        binding.statusTitle.text = getString(R.string.termux_detected_title, app.label)
        binding.statusSummary.text = getString(
            R.string.termux_detected_summary,
            app.packageName,
            app.versionName ?: getString(R.string.termux_version_unknown)
        )
        binding.detectedPackage.text = app.packageName
        binding.authorizationStatus.text = if (isAuthorized) {
            getString(R.string.termux_authorization_granted)
        } else {
            getString(R.string.termux_authorization_not_granted)
        }
        binding.exportStatus.text = if (exportDir.isNullOrBlank()) {
            getString(R.string.termux_export_status_missing)
        } else {
            getString(R.string.termux_export_status_ready, exportDir)
        }
        binding.serverStatus.text = if (serviceRunning) {
            getString(R.string.termux_service_running)
        } else {
            getString(R.string.termux_service_stopped)
        }
        binding.bootstrapCommand.text = buildBootstrapCommand(app)
        binding.verificationCommand.text = buildVerificationCommand(app)
        binding.btnGrantTermux.isEnabled = !isAuthorized && serviceRunning
        binding.btnOpenTermux.isEnabled = app.launchIntent != null
        binding.btnCopyBootstrap.isEnabled = true
        binding.btnCopyVerify.isEnabled = true
        binding.warningCard.isVisible = !serviceRunning || exportDir.isNullOrBlank()
        binding.warningText.text = buildWarnings(serviceRunning, exportDir.isNullOrBlank())
    }

    private fun detectTermuxApp(): TermuxApp? {
        val pm = packageManager
        val pkg = pm.getInstalledPackages(0)
            .filter { info ->
                val launchIntent = pm.getLaunchIntentForPackage(info.packageName)
                launchIntent != null && (
                    info.packageName.contains("termux", ignoreCase = true) ||
                        runCatching { info.applicationInfo?.loadLabel(pm)?.toString()?.contains("termux", ignoreCase = true) == true }
                            .getOrDefault(false)
                    )
            }
            .sortedBy { it.packageName != "com.termux" }
            .firstOrNull() ?: return null

        val label = runCatching { pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName }.getOrDefault(pkg.packageName)
        val uid = pkg.applicationInfo?.uid ?: return null
        return TermuxApp(
            packageName = pkg.packageName,
            versionName = pkg.versionName,
            uid = uid,
            label = label,
            launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
        )
    }

    private fun grantTermuxAccess() {
        val app = detectedApp ?: return
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    AuthorizationManager.grant(app.packageName, app.uid)
                    true
                }.getOrDefault(false)
            }
            if (success) {
                ActivityLogManager.log(app.label, app.packageName, "GRANT_TERMUX")
                Toast.makeText(this@TermuxIntegrationActivity, R.string.termux_grant_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@TermuxIntegrationActivity, R.string.termux_grant_failed, Toast.LENGTH_LONG).show()
            }
            refreshStatus()
        }
    }

    private fun openTermux() {
        detectedApp?.launchIntent?.let { startActivity(it) }
    }

    private fun copyBootstrapCommand() {
        val app = detectedApp ?: return
        copyText(getString(R.string.termux_copy_bootstrap_label), buildBootstrapCommand(app))
        ActivityLogManager.log(app.label, app.packageName, "COPY_TERMUX_BOOTSTRAP")
    }

    private fun copyVerificationCommand() {
        val app = detectedApp ?: return
        copyText(getString(R.string.termux_copy_verify_label), buildVerificationCommand(app))
        ActivityLogManager.log(app.label, app.packageName, "COPY_TERMUX_VERIFY")
    }

    private fun copyText(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.termux_copied, Toast.LENGTH_SHORT).show()
    }

    private fun buildBootstrapCommand(app: TermuxApp): String {
        val exportHint = if (ShizukuSettings.getExportDirUri().isNullOrBlank()) {
            "# Export rish/plus/su from the Shizuku+ terminal tutorial first\n"
        } else {
            ""
        }
        return buildString {
            append(exportHint)
            append("cd \"\$HOME\"\n")
            append("chmod 700 ./rish ./plus ./su 2>/dev/null || true\n")
            append("grep -q 'RISH_APPLICATION_ID=${app.packageName}' ~/.bashrc 2>/dev/null || echo 'export RISH_APPLICATION_ID=${app.packageName}' >> ~/.bashrc\n")
            append("grep -q 'alias rish=\"\$HOME/rish\"' ~/.bashrc 2>/dev/null || echo 'alias rish=\"\$HOME/rish\"' >> ~/.bashrc\n")
            append("grep -q 'alias plus=\"\$HOME/plus\"' ~/.bashrc 2>/dev/null || echo 'alias plus=\"\$HOME/plus\"' >> ~/.bashrc\n")
            append(". ~/.bashrc 2>/dev/null || true\n")
            append("sh \"\$HOME/rish\" -c id")
        }
    }

    private fun buildVerificationCommand(app: TermuxApp): String {
        return buildString {
            append("export RISH_APPLICATION_ID=${app.packageName}\n")
            append("sh \"\$HOME/rish\" -c 'id && getprop ro.build.version.release'")
        }
    }

    private fun buildWarnings(serviceRunning: Boolean, exportMissing: Boolean): String {
        val warnings = mutableListOf<String>()
        if (!serviceRunning) warnings += getString(R.string.termux_warning_service)
        if (exportMissing) warnings += getString(R.string.termux_warning_export)
        return warnings.joinToString("\n\n")
    }

    private data class TermuxApp(
        val packageName: String,
        val versionName: String?,
        val uid: Int,
        val label: String,
        val launchIntent: Intent?
    )
}
