package com.sukisu.ultra.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.sukisu.ultra.Natives
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.HanziToPinyin
import java.text.Collator
import java.util.*
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.TIMEOUT_MILLIS
import com.sukisu.ultra.ui.webui.getInstalledPackagesAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit

// 应用分类
enum class AppCategory(val displayNameRes: Int, val persistKey: String) {
    ALL(com.sukisu.ultra.R.string.category_all_apps, "ALL"),
    ROOT(com.sukisu.ultra.R.string.category_root_apps, "ROOT"),
    CUSTOM(com.sukisu.ultra.R.string.category_custom_apps, "CUSTOM"),
    DEFAULT(com.sukisu.ultra.R.string.category_default_apps, "DEFAULT");

    companion object {
        fun fromPersistKey(key: String): AppCategory {
            return entries.find { it.persistKey == key } ?: ALL
        }
    }
}

// 排序方式
enum class SortType(val displayNameRes: Int, val persistKey: String) {
    NAME_ASC(com.sukisu.ultra.R.string.sort_name_asc, "NAME_ASC"),
    NAME_DESC(com.sukisu.ultra.R.string.sort_name_desc, "NAME_DESC"),
    INSTALL_TIME_NEW(com.sukisu.ultra.R.string.sort_install_time_new, "INSTALL_TIME_NEW"),
    INSTALL_TIME_OLD(com.sukisu.ultra.R.string.sort_install_time_old, "INSTALL_TIME_OLD"),
    SIZE_DESC(com.sukisu.ultra.R.string.sort_size_desc, "SIZE_DESC"),
    SIZE_ASC(com.sukisu.ultra.R.string.sort_size_asc, "SIZE_ASC"),
    USAGE_FREQ(com.sukisu.ultra.R.string.sort_usage_freq, "USAGE_FREQ");

    companion object {
        fun fromPersistKey(key: String): SortType {
            return entries.find { it.persistKey == key } ?: NAME_ASC
        }
    }
}

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
class SuperUserViewModel : ViewModel() {
    val isPlatformAlive get() = Platform.isAlive
    companion object {
        private const val TAG = "SuperUserViewModel"
        var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private const val PREFS_NAME = "settings"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val KEY_SELECTED_CATEGORY = "selected_category"
        private const val KEY_CURRENT_SORT_TYPE = "current_sort_type"
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
        val profile: Natives.Profile?,
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo!!.uid

        val allowSu: Boolean
            get() = profile != null && profile.allowSu
        val hasCustomProfile: Boolean
            get() {
                if (profile == null) {
                    return false
                }
                return if (profile.allowSu) {
                    !profile.rootUseDefault
                } else {
                    !profile.nonRootUseDefault
                }
            }
    }

    private val prefs: SharedPreferences = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var search by mutableStateOf("")

    var showSystemApps by mutableStateOf(loadShowSystemApps())
        private set

    var selectedCategory by mutableStateOf(loadSelectedCategory())
        private set

    var currentSortType by mutableStateOf(loadCurrentSortType())
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    // 批量操作相关状态
    var showBatchActions by mutableStateOf(false)
        internal set
    var selectedApps by mutableStateOf<Set<String>>(emptySet())
        internal set

    /**
     * 从SharedPreferences加载显示系统应用设置
     */
    private fun loadShowSystemApps(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
    }

    /**
     * 从SharedPreferences加载选择的应用分类
     */
    private fun loadSelectedCategory(): AppCategory {
        val categoryKey = prefs.getString(KEY_SELECTED_CATEGORY, AppCategory.ALL.persistKey) ?: AppCategory.ALL.persistKey
        return AppCategory.fromPersistKey(categoryKey)
    }

    /**
     * 从SharedPreferences加载当前排序方式
     */
    private fun loadCurrentSortType(): SortType {
        val sortKey = prefs.getString(KEY_CURRENT_SORT_TYPE, SortType.NAME_ASC.persistKey) ?: SortType.NAME_ASC.persistKey
        return SortType.fromPersistKey(sortKey)
    }

    /**
     * 更新显示系统应用设置并保存到SharedPreferences
     */
    fun updateShowSystemApps(newValue: Boolean) {
        showSystemApps = newValue
        saveShowSystemApps(newValue)
    }

    /**
     * 更新选择的应用分类并保存到SharedPreferences
     */
    fun updateSelectedCategory(newCategory: AppCategory) {
        selectedCategory = newCategory
        saveSelectedCategory(newCategory)
    }

    /**
     * 更新当前排序方式并保存到SharedPreferences
     */
    fun updateCurrentSortType(newSortType: SortType) {
        currentSortType = newSortType
        saveCurrentSortType(newSortType)
    }

    /**
     * 保存显示系统应用设置到SharedPreferences
     */
    private fun saveShowSystemApps(value: Boolean) {
        prefs.edit {
            putBoolean(KEY_SHOW_SYSTEM_APPS, value)
        }
        Log.d(TAG, "Saved show system apps: $value")
    }

    /**
     * 保存选择的应用分类到SharedPreferences
     */
    private fun saveSelectedCategory(category: AppCategory) {
        prefs.edit {
            putString(KEY_SELECTED_CATEGORY, category.persistKey)
        }
        Log.d(TAG, "Saved selected category: ${category.persistKey}")
    }

    /**
     * 保存当前排序方式到SharedPreferences
     */
    private fun saveCurrentSortType(sortType: SortType) {
        prefs.edit {
            putString(KEY_CURRENT_SORT_TYPE, sortType.persistKey)
        }
        Log.d(TAG, "Saved current sort type: ${sortType.persistKey}")
    }

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.allowSu -> 0
                it.hasCustomProfile -> 1
                else -> 2
            }
        }.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        apps.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    val appList by derivedStateOf {
        sortedList.filter {
            it.label.contains(search, true) || it.packageName.contains(
                search,
                true
            ) || HanziToPinyin.getInstance()
                .toPinyinString(it.label).contains(search, true)
        }.filter {
            it.uid == 2000 || showSystemApps || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    // 切换批量操作模式
    fun toggleBatchMode() {
        showBatchActions = !showBatchActions
        if (!showBatchActions) {
            clearSelection()
        }
    }

    // 切换应用选择状态
    fun toggleAppSelection(packageName: String) {
        selectedApps = if (selectedApps.contains(packageName)) {
            selectedApps - packageName
        } else {
            selectedApps + packageName
        }
    }

    // 清除所有选择
    fun clearSelection() {
        selectedApps = emptySet()
    }

    // 批量更新权限
    suspend fun updateBatchPermissions(allowSu: Boolean) {
        selectedApps.forEach { packageName ->
            val app = apps.find { it.packageName == packageName }
            app?.let {
                val profile = Natives.getAppProfile(packageName, it.uid)
                val updatedProfile = profile.copy(allowSu = allowSu)
                if (Natives.setAppProfile(updatedProfile)) {
                    apps = apps.map { app ->
                        if (app.packageName == packageName) {
                            app.copy(profile = updatedProfile)
                        } else {
                            app
                        }
                    }
                }
            }
        }
        clearSelection()
        showBatchActions = false // 批量操作完成后退出批量模式
        fetchAppList() // 刷新列表以显示最新状态
    }

    // 批量更新权限和umount模块设置
    suspend fun updateBatchPermissions(allowSu: Boolean, umountModules: Boolean? = null) {
        selectedApps.forEach { packageName ->
            val app = apps.find { it.packageName == packageName }
            app?.let {
                val profile = Natives.getAppProfile(packageName, it.uid)
                val updatedProfile = profile.copy(
                    allowSu = allowSu,
                    umountModules = umountModules ?: profile.umountModules,
                    nonRootUseDefault = false
                )
                if (Natives.setAppProfile(updatedProfile)) {
                    apps = apps.map { app ->
                        if (app.packageName == packageName) {
                            app.copy(profile = updatedProfile)
                        } else {
                            app
                        }
                    }
                }
            }
        }
        clearSelection()
        showBatchActions = false // 批量操作完成后退出批量模式
        fetchAppList() // 刷新列表以显示最新状态
    }

    // 更新本地应用配置
    fun updateAppProfileLocally(packageName: String, updatedProfile: Natives.Profile) {
        apps = apps.map { app ->
            if (app.packageName == packageName) {
                app.copy(profile = updatedProfile)
            } else {
                app
            }
        }
    }

    suspend fun fetchAppList() {
        isRefreshing = true

        withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                while (!isPlatformAlive) {
                    delay(500)
                }
            } ?: return@withContext // Exit early if timeout
            val pm = ksuApp.packageManager
            val start = SystemClock.elapsedRealtime()

            val packages = Platform.getInstalledPackagesAll {
                Log.e(TAG, "getInstalledPackagesAll:", it)
                Toast.makeText(ksuApp, "Something went wrong, check logs", Toast.LENGTH_SHORT).show()
            }
            apps = packages.map {
                val appInfo = it.applicationInfo
                val uid = appInfo!!.uid
                val profile = Natives.getAppProfile(it.packageName, uid)
                AppInfo(
                    label = appInfo.loadLabel(pm).toString(),
                    packageInfo = it,
                    profile = profile,
                )
            }.filter { it.packageName != ksuApp.packageName }
            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}")
        }
    }
}