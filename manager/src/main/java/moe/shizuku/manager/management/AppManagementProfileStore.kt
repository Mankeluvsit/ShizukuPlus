package moe.shizuku.manager.management

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppManagementProfile(
    val name: String,
    val sortOrder: SortOrder,
    val filterState: FilterState,
    val searchQuery: String,
    val hiddenPackages: Set<String>
)

object AppManagementProfileStore {

    private const val PREFS_NAME = "app_management_prefs"
    private const val KEY_PROFILES = "saved_profiles"

    fun list(context: Context): List<AppManagementProfile> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFILES, null) ?: return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val names = root.keys().asSequence().toList().sorted()
        return names.mapNotNull { name ->
            val json = root.optJSONObject(name) ?: return@mapNotNull null
            AppManagementProfile(
                name = name,
                sortOrder = runCatching { SortOrder.valueOf(json.optString("sortOrder", SortOrder.NAME_ASC.name)) }
                    .getOrDefault(SortOrder.NAME_ASC),
                filterState = runCatching { FilterState.valueOf(json.optString("filterState", FilterState.ALL.name)) }
                    .getOrDefault(FilterState.ALL),
                searchQuery = json.optString("searchQuery", ""),
                hiddenPackages = buildSet {
                    val array = json.optJSONArray("hiddenPackages") ?: JSONArray()
                    for (i in 0 until array.length()) add(array.optString(i))
                }
            )
        }
    }

    fun save(context: Context, profile: AppManagementProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_PROFILES, null) ?: "{}") }.getOrDefault(JSONObject())
        root.put(profile.name, JSONObject().apply {
            put("sortOrder", profile.sortOrder.name)
            put("filterState", profile.filterState.name)
            put("searchQuery", profile.searchQuery)
            put("hiddenPackages", JSONArray(profile.hiddenPackages.sorted()))
        })
        prefs.edit().putString(KEY_PROFILES, root.toString()).apply()
    }

    fun delete(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_PROFILES, null) ?: "{}") }.getOrDefault(JSONObject())
        root.remove(name)
        prefs.edit().putString(KEY_PROFILES, root.toString()).apply()
    }
}
