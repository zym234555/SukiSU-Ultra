package com.sukisu.ultra.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.platform.model.ModuleConfig
import com.dergoogler.mmrl.platform.model.ModuleConfig.Companion.asModuleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sukisu.ultra.ui.util.HanziToPinyin
import com.sukisu.ultra.ui.util.listModules
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.Collator
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.pow

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
class ModuleViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
        private const val CUSTOM_USER_AGENT = "SukiSU-Ultra/2.0"
    }

    fun getModuleSize(dirId: String): String {
        val size = getModuleFolderSize(dirId)
        return formatFileSize(size)
    }

    class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
        val dirId: String, // real module id (dir name)
        var config: ModuleConfig? = null,
    )

    var isRefreshing by mutableStateOf(false)
        private set
    var search by mutableStateOf("")

    var sortEnabledFirst by mutableStateOf(false)
    var sortActionFirst by mutableStateOf(false)
    val moduleList by derivedStateOf {
        val comparator =
            compareBy<ModuleInfo>(
                { if (sortEnabledFirst) !it.enabled else 0 },
                { if (sortActionFirst) !it.hasWebUi && !it.hasActionScript else 0 },
            ).thenBy(Collator.getInstance(Locale.getDefault()), ModuleInfo::id)
        modules.filter {
            it.id.contains(search, true) || it.name.contains(search, true) || HanziToPinyin.getInstance()
                .toPinyinString(it.name).contains(search, true)
        }.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true

            val oldModuleList = modules

            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {
                val result = listModules()

                Log.i(TAG, "result: $result")

                val array = JSONArray(result)
                modules = (0 until array.length())
                    .asSequence()
                    .map { array.getJSONObject(it) }
                    .map { obj ->
                        ModuleInfo(
                            obj.getString("id"),
                            obj.optString("name"),
                            obj.optString("author", "Unknown"),
                            obj.optString("version", "Unknown"),
                            obj.optInt("versionCode", 0),
                            obj.optString("description"),
                            obj.getBoolean("enabled"),
                            obj.getBoolean("update"),
                            obj.getBoolean("remove"),
                            obj.optString("updateJson"),
                            obj.optBoolean("web"),
                            obj.optBoolean("action"),
                            obj.getString("dir_id")
                        )
                    }.toList()
                launch {
                    modules.forEach { module ->
                        withContext(Dispatchers.IO) {
                            try {
                                runCatching {
                                    module.config = module.id.asModuleConfig
                                }.onFailure { e ->
                                    Log.e(TAG, "Failed to load config from id for module ${module.id}", e)
                                }
                                if (module.config == null) {
                                    runCatching {
                                        module.config = module.name.asModuleConfig
                                    }.onFailure { e ->
                                        Log.e(TAG, "Failed to load config from name for module ${module.id}", e)
                                    }
                                }
                                if (module.config == null) {
                                    runCatching {
                                        module.config = module.description.asModuleConfig
                                    }.onFailure { e ->
                                        Log.e(TAG, "Failed to load config from description for module ${module.id}", e)
                                    }
                                }
                                if (module.config == null) {
                                    module.config = ModuleConfig()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load any config for module ${module.id}", e)
                                module.config = ModuleConfig()
                            }
                        }
                    }
                }
                isNeedRefresh = false
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                isRefreshing = false
            }

            // when both old and new is kotlin.collections.EmptyList
            // moduleList update will don't trigger
            if (oldModuleList === modules) {
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(m: ModuleInfo): Triple<String, String, String> {
        val empty = Triple("", "", "")
        if (m.updateJson.isEmpty() || m.remove || m.update || !m.enabled) {
            return empty
        }
        // download updateJson
        val result = kotlin.runCatching {
            val url = m.updateJson
            Log.i(TAG, "checkUpdate url: $url")

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", CUSTOM_USER_AGENT)
                .build()

            val response = client.newCall(request).execute()

            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                Log.d(TAG, "checkUpdate failed: ${response.message}")
                ""
            }
        }.getOrElse { e ->
            Log.e(TAG, "checkUpdate exception", e)
            ""
        }

        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return empty
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return empty

        var version = updateJson.optString("version", "")
        version = sanitizeVersionString(version)
        val versionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (versionCode <= m.versionCode || zipUrl.isEmpty()) {
            return empty
        }

        return Triple(zipUrl, version, changelog)
    }
}

/**
 * 格式化文件大小的工具函数
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

    return DecimalFormat("#,##0.#").format(
        bytes / 1024.0.pow(digitGroups.toDouble())
    ) + " " + units[digitGroups]
}

/**
 * 使用 su 权限调用 du 命令获取模块文件夹大小
 */
fun getModuleFolderSize(dirId: String): Long {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "du -sb /data/adb/modules/$dirId"))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readLine()
        process.waitFor()
        reader.close()

        if (output != null) {
            val sizeStr = output.split("\t").firstOrNull()
            sizeStr?.toLongOrNull() ?: 0L
        } else {
            0L
        }
    } catch (e: Exception) {
        Log.e("ModuleItem", "Error calculating module size with su for $dirId: ${e.message}")
        0L
    }
}

/**
 * 递归计算目录大小（备用）
 */
private fun calculateDirectorySize(directory: File): Long {
    var size = 0L
    try {
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
    } catch (e: Exception) {
        Log.e("ModuleItem", "Error calculating directory size: ${e.message}")
    }
    return size
}