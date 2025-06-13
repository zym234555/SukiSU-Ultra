package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.sukisu.ultra.R
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.File

/**
 * SuSFS 配置管理器
 * 用于管理SuSFS相关的配置和命令执行
 */
object SuSFSManager {
    private const val PREFS_NAME = "susfs_config"
    private const val KEY_UNAME_VALUE = "uname_value"
    private const val KEY_IS_ENABLED = "is_enabled"
    private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
    private const val KEY_LAST_APPLIED_VALUE = "last_applied_value"
    private const val SUSFS_BINARY_NAME = "ksu_susfs"
    private const val DEFAULT_UNAME = "default"
    private const val STARTUP_SCRIPT_PATH = "/data/adb/service.d/susfs_startup.sh"
    private const val SUSFS_TARGET_PATH = "/data/adb/ksu/bin/$SUSFS_BINARY_NAME"

    /**
     * 获取Root Shell实例
     */
    private fun getRootShell(): Shell {
        return Shell.getShell()
    }

    /**
     * 获取SuSFS配置的SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存uname值
     */
    fun saveUnameValue(context: Context, value: String) {
        getPrefs(context).edit().apply {
            putString(KEY_UNAME_VALUE, value)
            apply()
        }
    }

    /**
     * 获取保存的uname值
     */
    fun getUnameValue(context: Context): String {
        return getPrefs(context).getString(KEY_UNAME_VALUE, DEFAULT_UNAME) ?: DEFAULT_UNAME
    }

    /**
     * 保存最后应用的值
     */
    private fun saveLastAppliedValue(context: Context, value: String) {
        getPrefs(context).edit().apply {
            putString(KEY_LAST_APPLIED_VALUE, value)
            apply()
        }
    }

    /**
     * 获取最后应用的值
     */
    fun getLastAppliedValue(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_APPLIED_VALUE, DEFAULT_UNAME) ?: DEFAULT_UNAME
    }

    /**
     * 保存SuSFS启用状态
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_IS_ENABLED, enabled)
            apply()
        }
    }

    /**
     * 设置开机自启动状态
     */
    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_AUTO_START_ENABLED, enabled)
            apply()
        }
    }

    /**
     * 获取开机自启动状态
     */
    fun isAutoStartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_START_ENABLED, false)
    }

    /**
     * 从assets复制ksu_susfs文件到/data/adb/ksu/bin/
     */
    private suspend fun copyBinaryFromAssets(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(SUSFS_BINARY_NAME)
            val tempFile = File(context.cacheDir, SUSFS_BINARY_NAME)

            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            // 创建目标目录并复制文件到/data/adb/ksu/bin/
            val shell = getRootShell()
            val commands = arrayOf(
                "cp '${tempFile.absolutePath}' '$SUSFS_TARGET_PATH'",
                "chmod 755 '$SUSFS_TARGET_PATH'",
            )

            var success = true
            for (command in commands) {
                val result = shell.newJob().add(command).exec()
                if (!result.isSuccess) {
                    success = false
                    break
                }
            }

            // 清理临时文件
            tempFile.delete()

            if (success) {
                val verifyResult = shell.newJob().add("test -f '$SUSFS_TARGET_PATH'").exec()
                if (verifyResult.isSuccess) {
                    SUSFS_TARGET_PATH
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 创建开机自启动脚本
     */
    private suspend fun createStartupScript(unameValue: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val scriptContent = """#!/system/bin/sh
# SuSFS 开机自启动脚本
# 由 KernelSU Manager 自动生成

# 等待系统完全启动
sleep 30

# 检查二进制文件是否存在
if [ -f "$SUSFS_TARGET_PATH" ]; then
    # 执行 SuSFS setUname 命令
    $SUSFS_TARGET_PATH set_uname '$unameValue' '$DEFAULT_UNAME'
    
    # 记录日志
    echo "\$(date): SuSFS setUname executed with value: $unameValue" >> /data/adb/ksu/log/susfs_startup.log
else
    echo "\$(date): SuSFS binary not found at $SUSFS_TARGET_PATH" >> /data/adb/ksu/log/susfs_startup.log
fi
"""

            val shell = getRootShell()
            val commands = arrayOf(
                "mkdir -p /data/adb/service.d",
                "cat > $STARTUP_SCRIPT_PATH << 'EOF'\n$scriptContent\nEOF",
                "chmod 755 $STARTUP_SCRIPT_PATH"
            )

            var success = true
            for (command in commands) {
                val result = shell.newJob().add(command).exec()
                if (!result.isSuccess) {
                    success = false
                    break
                }
            }

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除开机自启动脚本
     */
    private suspend fun removeStartupScript(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val result = shell.newJob().add("rm -f $STARTUP_SCRIPT_PATH").exec()
            result.isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行SuSFS命令设置uname
     */
    suspend fun setUname(context: Context, unameValue: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 首先复制二进制文件到/data/adb/ksu/bin/
            val binaryPath = copyBinaryFromAssets(context)
            if (binaryPath == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.susfs_binary_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext false
            }

            // 构建命令
            val command = "$binaryPath set_uname '$unameValue' '$DEFAULT_UNAME'"

            // 执行命令
            val result = getRootShell().newJob().add(command).exec()

            if (result.isSuccess) {
                // 保存配置
                saveUnameValue(context, unameValue)
                saveLastAppliedValue(context, unameValue)
                setEnabled(context, true)

                // 如果开启了开机自启动，更新启动脚本
                if (isAutoStartEnabled(context)) {
                    createStartupScript(unameValue)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.susfs_uname_set_success, unameValue),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            } else {
                withContext(Dispatchers.Main) {
                    val errorOutput = result.out.joinToString("\n") + "\n" + result.err.joinToString("\n")
                    Toast.makeText(
                        context,
                        context.getString(R.string.susfs_command_failed) + "\n$errorOutput",
                        Toast.LENGTH_LONG
                    ).show()
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.susfs_command_error, e.message ?: "Unknown error"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    /**
     * 配置开机自启动
     */
    suspend fun configureAutoStart(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                // 启用开机自启动
                val lastValue = getLastAppliedValue(context)
                if (lastValue == DEFAULT_UNAME) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.susfs_no_config_to_autostart),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@withContext false
                }

                // 确保二进制文件存在于目标位置
                val shell = getRootShell()
                val checkResult = shell.newJob().add("test -f '$SUSFS_TARGET_PATH'").exec()

                if (!checkResult.isSuccess) {
                    // 如果不存在，尝试复制
                    val binaryPath = copyBinaryFromAssets(context)
                    if (binaryPath == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.susfs_binary_not_found),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@withContext false
                    }
                }

                val success = createStartupScript(lastValue)
                if (success) {
                    setAutoStartEnabled(context, true)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.susfs_autostart_enabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.susfs_autostart_enable_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                success
            } else {
                // 禁用开机自启动
                val success = removeStartupScript()
                if (success) {
                    setAutoStartEnabled(context, false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.susfs_autostart_disabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.susfs_autostart_disable_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                success
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.susfs_autostart_error, e.message ?: "Unknown error"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    /**
     * 重置为默认值
     */
    suspend fun resetToDefault(context: Context): Boolean {
        val success = setUname(context, DEFAULT_UNAME)
        if (success) {
            // 重置时清除最后应用的值
            saveLastAppliedValue(context, DEFAULT_UNAME)
            // 如果开启了开机自启动，需要禁用它
            if (isAutoStartEnabled(context)) {
                configureAutoStart(context, false)
            }
        }
        return success
    }

    /**
     * 检查ksu_susfs文件是否存在于assets中
     */
    fun isBinaryAvailable(context: Context): Boolean {
        return try {
            context.assets.open(SUSFS_BINARY_NAME).use { true }
        } catch (_: IOException) {
            false
        }
    }
}