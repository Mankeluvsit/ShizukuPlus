package moe.shizuku.manager.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.ActivityLogItemBinding
import moe.shizuku.manager.databinding.AppsActivityBinding
import moe.shizuku.manager.databinding.AppsAppbarActivityBinding
import moe.shizuku.manager.utils.ActivityLogManager
import moe.shizuku.manager.utils.ActivityLogRecord
import moe.shizuku.manager.utils.EmptyStateView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityLogActivity : AppBarActivity() {

    private val adapter = LogAdapter()
    private lateinit var emptyStateView: EmptyStateView
    private lateinit var appsBinding: AppsActivityBinding
    private var allRecords: List<ActivityLogRecord> = emptyList()
    private var currentFilter: LogFilter = LogFilter.ALL
    private var currentQuery = ""

    override fun getLayoutId() = R.layout.apps_appbar_activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appsBinding = AppsActivityBinding.inflate(layoutInflater, rootView, false)
        setContentView(appsBinding.root)

        val appbarBinding = AppsAppbarActivityBinding.bind(rootView)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_activity_log)

        appbarBinding.searchLayout.visibility = View.VISIBLE
        appbarBinding.searchEditText.hint = getString(R.string.settings_activity_log_search_hint)
        appbarBinding.searchEditText.doOnTextChanged { text, _, _, _ ->
            currentQuery = text?.toString().orEmpty()
            applyFilter()
        }

        appbarBinding.chipAll.text = getString(R.string.log_filter_all)
        appbarBinding.chipGranted.text = getString(R.string.log_filter_grants)
        appbarBinding.chipDenied.text = getString(R.string.log_filter_revokes)
        appbarBinding.chipHidden.text = getString(R.string.log_filter_termux)
        appbarBinding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                appbarBinding.chipGranted.id -> LogFilter.GRANTS
                appbarBinding.chipDenied.id -> LogFilter.REVOKES
                appbarBinding.chipHidden.id -> LogFilter.TERMUX
                else -> LogFilter.ALL
            }
            applyFilter()
        }

        emptyStateView = appsBinding.emptyStateView
        emptyStateView.hideActionButton()
        showDefaultEmptyState()

        ViewCompat.setOnApplyWindowInsetsListener(appsBinding.list) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        appsBinding.list.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ActivityLogManager.logs.collectLatest { records ->
                    allRecords = records
                    applyFilter()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.settings_activity_log_clear).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_delete_24dp)
        }
        menu.add(0, 2, 1, R.string.common_copy).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_copy)
        }
        menu.add(0, 3, 2, R.string.common_share).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setIcon(R.drawable.ic_share_24)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1 -> {
                ActivityLogManager.clear()
                true
            }
            2 -> {
                copyLogs()
                true
            }
            3 -> {
                shareLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyFilter() {
        val query = currentQuery.trim()
        val filtered = allRecords.filter { record ->
            val matchesQuery = query.isBlank() ||
                record.appName.contains(query, ignoreCase = true) ||
                record.packageName.contains(query, ignoreCase = true) ||
                record.action.contains(query, ignoreCase = true)
            val matchesFilter = when (currentFilter) {
                LogFilter.ALL -> true
                LogFilter.GRANTS -> record.action.contains("grant", ignoreCase = true) ||
                    record.action.contains("allow", ignoreCase = true)
                LogFilter.REVOKES -> record.action.contains("revoke", ignoreCase = true) ||
                    record.action.contains("deny", ignoreCase = true)
                LogFilter.TERMUX -> record.packageName.contains("termux", ignoreCase = true) ||
                    record.appName.contains("termux", ignoreCase = true) ||
                    record.action.contains("termux", ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }

        adapter.update(filtered)
        val isEmpty = filtered.isEmpty()
        if (isEmpty) {
            if (query.isNotBlank()) {
                emptyStateView.setIcon(R.drawable.ic_empty_search_24)
                emptyStateView.setTitle(R.string.empty_state_title_activity_log_search)
                emptyStateView.setDescription(R.string.empty_state_description_activity_log_search)
            } else {
                showDefaultEmptyState()
            }
        }
        emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        appsBinding.list.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDefaultEmptyState() {
        emptyStateView.setIcon(R.drawable.ic_empty_log_24)
        emptyStateView.setTitle(R.string.empty_state_title_activity_log_empty)
        emptyStateView.setDescription(R.string.empty_state_description_activity_log_empty)
    }

    private fun buildExportText(): String {
        val visibleRecords = adapter.items
        if (visibleRecords.isEmpty()) {
            return getString(R.string.settings_activity_log_empty)
        }

        return buildString {
            append(getString(R.string.settings_activity_log))
            append('\n')
            append(getString(R.string.settings_activity_log_export_summary, visibleRecords.size))
            append("\n\n")
            visibleRecords.forEach { record ->
                append(LogViewHolder.dateFormat.format(Date(record.timestamp)))
                append("  ")
                append(record.action)
                append("  ")
                append(record.appName.ifBlank { record.packageName })
                append("  (")
                append(record.packageName)
                append(")\n")
            }
        }
    }

    private fun copyLogs() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.settings_activity_log), buildExportText()))
    }

    private fun shareLogs() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildExportText())
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.common_share)))
    }

    private class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
        var items: List<ActivityLogRecord> = emptyList()
            private set

        fun update(newItems: List<ActivityLogRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LogViewHolder.create(parent)
        override fun onBindViewHolder(holder: LogViewHolder, position: Int) = holder.bind(items[position])
    }

    private class LogViewHolder(private val binding: ActivityLogItemBinding) : RecyclerView.ViewHolder(binding.root) {
        companion object {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            fun create(parent: ViewGroup) =
                LogViewHolder(ActivityLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        fun bind(record: ActivityLogRecord) {
            val context = binding.root.context
            val pm = context.packageManager

            try {
                val ai = pm.getApplicationInfo(record.packageName, 0)
                binding.appName.text = ai.loadLabel(pm)
                binding.icon.setImageDrawable(ai.loadIcon(pm))
            } catch (_: Exception) {
                binding.appName.text = record.appName.ifEmpty { record.packageName }
                binding.icon.setImageResource(R.drawable.ic_system_icon)
            }

            binding.packageName.text = record.packageName
            binding.action.text = record.action
            binding.timestamp.text = dateFormat.format(Date(record.timestamp))
        }
    }

    private enum class LogFilter { ALL, GRANTS, REVOKES, TERMUX }
}
