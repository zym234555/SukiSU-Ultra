package com.sukisu.ultra.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.sukisu.ultra.Natives
import com.sukisu.ultra.ksuApp
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import java.io.File


/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

private val ksuDaemonPath by lazy {
    "${ksuApp.applicationInfo.nativeLibraryDir}${File.separator}libzakozako.so"
}


object KsuCli {
    val SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

fun createRootShellBuilder(globalMnt: Boolean = false): Shell.Builder {
    return Shell.Builder.create().run {
        val cmd = buildString {
            append("$ksuDaemonPath debug su")
            if (globalMnt) append(" -g")
            append(" || ")
            append("su")
            if (globalMnt) append(" --mount-master")
            append(" || ")
            append("sh")
        }
        setCommands("sh", "-c", cmd)
    }
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    return runCatching {
        createRootShellBuilder(globalMnt).build()
    }.getOrElse { e ->
        Log.w(TAG, "su failed: ", e)
        Shell.Builder.create().apply {
            if (globalMnt) setFlags(Shell.FLAG_MOUNT_MASTER)
        }.build()
    }
}

fun execKsud(args: String, newShell: Boolean = false): Boolean {
    return if (newShell) {
        withNewRootShell {
            ShellUtils.fastCmdResult(this, "$ksuDaemonPath $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(), "$ksuDaemonPath $args")
    }
}

fun install() {
    val start = SystemClock.elapsedRealtime()
    val magiskboot = File(ksuApp.applicationInfo.nativeLibraryDir, "libzakoboot.so").absolutePath
    val result = execKsud("install --magiskboot $magiskboot", true)
    Log.w(TAG, "install result: $result, cost: ${SystemClock.elapsedRealtime() - start}ms")
}

fun listModules(): String {
    val shell = getRootShell()
    val out =
        shell.newJob().add("$ksuDaemonPath module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
}

fun getModuleCount(): Int {
    return runCatching {
        JSONArray(listModules()).length()
    }.getOrDefault(0)
}

fun getSuperuserCount(): Int {
    return Natives.allowList.size
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable $id"
    } else {
        "module disable $id"
    }
    val result = execKsud(cmd, true)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

fun restoreModule(id: String): Boolean {
    val cmd = "module restore $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "restore module $id result: $result")
    return result
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    return withNewRootShell {
        newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    }
}

fun flashModule(
    uri: Uri,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Boolean {
    val resolver = ksuApp.contentResolver
    with(resolver.openInputStream(uri)) {
        val file = File(ksuApp.cacheDir, "module.zip")
        file.outputStream().use { output ->
            this?.copyTo(output)
        }
        val cmd = "module install ${file.absolutePath}"
        val result = flashWithIO("$ksuDaemonPath $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install module $uri result: $result")

        file.delete()

        onFinish(result.isSuccess, result.code)
        return result.isSuccess
    }
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val shell = createRootShell(true)

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = shell.newJob().add("$ksuDaemonPath module action $moduleId")
        .to(stdoutCallback, stderrCallback).exec()
    Log.i("KernelSU", "Module runAction result: $result")

    return result.isSuccess
}

fun restoreBoot(
    onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val magiskboot = File(ksuApp.applicationInfo.nativeLibraryDir, "libzakoboot.so")
    val result = flashWithIO(
        "$ksuDaemonPath boot-restore -f --magiskboot $magiskboot",
        onStdout,
        onStderr
    )
    onFinish(result.isSuccess, result.code)
    return result.isSuccess
}

fun uninstallPermanently(
    onFinish: (Boolean, Int) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val magiskboot = File(ksuApp.applicationInfo.nativeLibraryDir, "libzakoboot.so")
    val result =
        flashWithIO("$ksuDaemonPath uninstall --magiskboot $magiskboot", onStdout, onStderr)
    onFinish(result.isSuccess, result.code)
    return result.isSuccess
}

@Parcelize
sealed class LkmSelection : Parcelable {
    data class LkmUri(val uri: Uri) : LkmSelection()
    data class KmiString(val value: String) : LkmSelection()
    data object KmiNone : LkmSelection()
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): Boolean {
    val resolver = ksuApp.contentResolver

    val bootFile = bootUri?.let { uri ->
        with(resolver.openInputStream(uri)) {
            val bootFile = File(ksuApp.cacheDir, "boot.img")
            bootFile.outputStream().use { output ->
                this?.copyTo(output)
            }

            bootFile
        }
    }

    val magiskboot = File(ksuApp.applicationInfo.nativeLibraryDir, "libzakoboot.so")
    var cmd = "boot-patch --magiskboot ${magiskboot.absolutePath}"

    cmd += if (bootFile == null) {
        // no boot.img, use -f to force install
        " -f"
    } else {
        " -b ${bootFile.absolutePath}"
    }

    if (ota) {
        cmd += " -u"
    }

    var lkmFile: File? = null
    when (lkm) {
        is LkmSelection.LkmUri -> {
            lkmFile = with(resolver.openInputStream(lkm.uri)) {
                val file = File(ksuApp.cacheDir, "kernelsu-tmp-lkm.ko")
                file.outputStream().use { output ->
                    this?.copyTo(output)
                }

                file
            }
            cmd += " -m ${lkmFile.absolutePath}"
        }

        is LkmSelection.KmiString -> {
            cmd += " --kmi ${lkm.value}"
        }

        LkmSelection.KmiNone -> {
            // do nothing
        }
    }

    // output dir
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    cmd += " -o $downloadsDir"

    val result = flashWithIO("$ksuDaemonPath $cmd", onStdout, onStderr)
    Log.i("KernelSU", "install boot result: ${result.isSuccess}")

    bootFile?.delete()
    lkmFile?.delete()

    // if boot uri is empty, it is direct install, when success, we should show reboot button
    onFinish(bootUri == null && result.isSuccess, result.code)
    return result.isSuccess
}

fun reboot(reason: String = "") {
    val shell = getRootShell()
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        ShellUtils.fastCmd(shell, "/system/bin/input keyevent 26")
    }
    ShellUtils.fastCmd(shell, "/system/bin/svc power reboot $reason || /system/bin/reboot $reason")
}

fun rootAvailable(): Boolean {
    val shell = getRootShell()
    return shell.isRoot
}

fun isAbDevice(): Boolean {
    val shell = getRootShell()
    return ShellUtils.fastCmd(shell, "getprop ro.build.ab_update").trim().toBoolean()
}

fun isInitBoot(): Boolean {
    return !Os.uname().release.contains("android12-")
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info current-kmi"
    ShellUtils.fastCmd(shell, "$ksuDaemonPath $cmd")
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info supported-kmi"
    val out = shell.newJob().add("$ksuDaemonPath $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

fun hasMagisk(): Boolean {
    val shell = getRootShell(true)
    val result = shell.newJob().add("which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
}

fun isSepolicyValid(rules: String?): Boolean {
    if (rules == null) {
        return true
    }
    val shell = getRootShell()
    val result =
        shell.newJob().add("$ksuDaemonPath sepolicy check '$rules'").to(ArrayList(), null)
            .exec()
    return result.isSuccess
}

fun getSepolicy(pkg: String): String {
    val shell = getRootShell()
    val result =
        shell.newJob().add("$ksuDaemonPath profile get-sepolicy $pkg").to(ArrayList(), null)
            .exec()
    Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
    return result.out.joinToString("\n")
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("$ksuDaemonPath profile set-sepolicy $pkg '$rules'")
        .to(ArrayList(), null).exec()
    Log.i(TAG, "set sepolicy result: ${result.code}")
    return result.isSuccess
}

fun listAppProfileTemplates(): List<String> {
    val shell = getRootShell()
    return shell.newJob().add("$ksuDaemonPath profile list-templates").to(ArrayList(), null)
        .exec().out
}

fun getAppProfileTemplate(id: String): String {
    val shell = getRootShell()
    return shell.newJob().add("$ksuDaemonPath profile get-template '${id}'")
        .to(ArrayList(), null).exec().out.joinToString("\n")
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    val shell = getRootShell()
    val escapedTemplate = template.replace("\"", "\\\"")
    val cmd = """$ksuDaemonPath profile set-template "$id" "$escapedTemplate'""""
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("$ksuDaemonPath profile delete-template '${id}'")
        .to(ArrayList(), null).exec().isSuccess
}

fun forceStopApp(packageName: String) {
    val shell = getRootShell()
    val result = shell.newJob().add("am force-stop $packageName").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String) {

    val shell = getRootShell()
    val result =
        shell.newJob()
            .add("cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n")
            .exec()
    Log.i(TAG, "launch $packageName result: $result")
}

fun restartApp(packageName: String) {
    forceStopApp(packageName)
    launchApp(packageName)
}

val suSFSDaemonPath by lazy {
    "${ksuApp.applicationInfo.nativeLibraryDir}${File.separator}libzakozakozako.so"
}

fun getSuSFS(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath support")
    return result
}

fun getSuSFSVersion(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath version")
    return result
}

fun getSuSFSVariant(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath variant")
    return result
}

fun getSuSFSFeatures(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath features")
    return result
}

fun susfsSUS_SU_0(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath sus_su 0")
    return result
}

fun susfsSUS_SU_2(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath sus_su 2")
    return result
}

fun susfsSUS_SU_Mode(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "$suSFSDaemonPath sus_su mode")
    return result
}

val kpmmgrPath by lazy {
    "${ksuApp.applicationInfo.nativeLibraryDir}${File.separator}libkpmmgr.so"
}


fun loadKpmModule(path: String, args: String? = null): String {
    return ShellUtils.fastCmd("$kpmmgrPath load $path ${args ?: ""}")
}

fun unloadKpmModule(name: String): String {
    return ShellUtils.fastCmd("$kpmmgrPath unload $name")
}

fun getKpmModuleCount(): Int {
    val result = ShellUtils.fastCmd("$kpmmgrPath num")
    return result.trim().toIntOrNull() ?: 0
}

fun runCmd(cmd: String): String {
    return Shell.cmd(cmd)
        .to(mutableListOf<String>(), null)
        .exec().out
        .joinToString("\n")
}

fun listKpmModules(): String {
    return runCmd("$kpmmgrPath list").trim()
}

fun getKpmModuleInfo(name: String): String {
    return runCmd("$kpmmgrPath info $name").trim()
}

fun controlKpmModule(name: String, args: String? = null): Int {
    val result = runCmd("""$kpmmgrPath control $name "${args ?: ""}"""")
    return result.trim().toIntOrNull() ?: -1
}

fun getKpmVersion(): String {
    return ShellUtils.fastCmd("$kpmmgrPath version").trim()
}

fun getZygiskImplement(): String {
    val zygiskPath = "/data/adb/modules/zygisksu"
    val rezygiskPath = "/data/adb/modules/rezygisk"
    val result = when {
        SuFile(zygiskPath, "module.prop").exists() && !SuFile(zygiskPath, "disable").exists() ->
            ShellUtils.fastCmd("grep '^name=' $zygiskPath/module.prop | cut -d'=' -f2")
        SuFile(rezygiskPath, "module.prop").exists() && !SuFile(rezygiskPath, "disable").exists() ->
            ShellUtils.fastCmd("grep '^name=' $rezygiskPath/module.prop | cut -d'=' -f2")
        else -> "None"
    }.trim()
    Log.i(TAG, "Zygisk implement: $result")
    return result
}
