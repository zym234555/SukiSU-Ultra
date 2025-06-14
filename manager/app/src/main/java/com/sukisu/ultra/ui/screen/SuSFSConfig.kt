package com.sukisu.ultra.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.theme.CardConfig
import com.sukisu.ultra.ui.util.SuSFSManager
import kotlinx.coroutines.launch

/**
 * 标签页枚举类
 */
enum class SuSFSTab(val displayNameRes: Int) {
    BASIC_SETTINGS(R.string.susfs_tab_basic_settings),
    SUS_PATHS(R.string.susfs_tab_sus_paths),
    SUS_MOUNTS(R.string.susfs_tab_sus_mounts),
    TRY_UMOUNT(R.string.susfs_tab_try_umount),
    PATH_SETTINGS(R.string.susfs_tab_path_settings),
    ENABLED_FEATURES(R.string.susfs_tab_enabled_features);

    companion object {
        fun getAllTabs(): List<SuSFSTab> = entries
    }
}

/**
 * SuSFS配置界面
 */
@SuppressLint("SdCardPath", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SuSFSConfigScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(SuSFSTab.BASIC_SETTINGS) }
    var unameValue by remember { mutableStateOf("") }
    var buildTimeValue by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmReset by remember { mutableStateOf(false) }
    var autoStartEnabled by remember { mutableStateOf(false) }
    var lastAppliedValue by remember { mutableStateOf("") }
    var lastAppliedBuildTime by remember { mutableStateOf("") }

    // 路径管理相关状态
    var susPaths by remember { mutableStateOf(emptySet<String>()) }
    var susMounts by remember { mutableStateOf(emptySet<String>()) }
    var tryUmounts by remember { mutableStateOf(emptySet<String>()) }
    var androidDataPath by remember { mutableStateOf("") }
    var sdcardPath by remember { mutableStateOf("") }

    // 启用功能状态相关
    var enabledFeatures by remember { mutableStateOf(emptyList<SuSFSManager.EnabledFeature>()) }
    var isLoadingFeatures by remember { mutableStateOf(false) }

    // 添加路径对话框状态
    var showAddPathDialog by remember { mutableStateOf(false) }
    var showAddMountDialog by remember { mutableStateOf(false) }
    var showAddUmountDialog by remember { mutableStateOf(false) }
    var showRunUmountDialog by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf("") }
    var newMount by remember { mutableStateOf("") }
    var newUmountPath by remember { mutableStateOf("") }
    var newUmountMode by remember { mutableIntStateOf(0) }
    var umountModeExpanded by remember { mutableStateOf(false) }

    // 重置确认对话框状态
    var showResetPathsDialog by remember { mutableStateOf(false) }
    var showResetMountsDialog by remember { mutableStateOf(false) }
    var showResetUmountsDialog by remember { mutableStateOf(false) }

    val allTabs = SuSFSTab.getAllTabs()

    // 实时判断是否可以启用开机自启动
    val canEnableAutoStart by remember {
        derivedStateOf {
            (unameValue.trim().isNotBlank() && unameValue.trim() != "default") ||
                    (buildTimeValue.trim().isNotBlank() && buildTimeValue.trim() != "default") ||
                    susPaths.isNotEmpty() || susMounts.isNotEmpty() || tryUmounts.isNotEmpty()
        }
    }

    // 加载启用功能状态
    fun loadEnabledFeatures() {
        coroutineScope.launch {
            isLoadingFeatures = true
            enabledFeatures = SuSFSManager.getEnabledFeatures(context)
            isLoadingFeatures = false
        }
    }

    // 加载当前配置
    LaunchedEffect(Unit) {
        unameValue = SuSFSManager.getUnameValue(context)
        buildTimeValue = SuSFSManager.getBuildTimeValue(context)
        autoStartEnabled = SuSFSManager.isAutoStartEnabled(context)
        lastAppliedValue = SuSFSManager.getLastAppliedValue(context)
        lastAppliedBuildTime = SuSFSManager.getLastAppliedBuildTime(context)
        susPaths = SuSFSManager.getSusPaths(context)
        susMounts = SuSFSManager.getSusMounts(context)
        tryUmounts = SuSFSManager.getTryUmounts(context)
        androidDataPath = SuSFSManager.getAndroidDataPath(context)
        sdcardPath = SuSFSManager.getSdcardPath(context)
    }

    // 当切换到启用功能状态标签页时加载数据
    LaunchedEffect(selectedTab) {
        if (selectedTab == SuSFSTab.ENABLED_FEATURES) {
            loadEnabledFeatures()
        }
    }

    // 当输入值变化时，自动调整开机自启动状态
    LaunchedEffect(canEnableAutoStart) {
        if (!canEnableAutoStart && autoStartEnabled) {
            autoStartEnabled = false
            SuSFSManager.configureAutoStart(context, false)
        }
    }

    // 各种对话框的定义保持不变
    // 添加路径对话框
    if (showAddPathDialog) {
        AlertDialog(
            onDismissRequest = { showAddPathDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_sus_path),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    label = { Text(stringResource(R.string.susfs_path_label)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPath.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                if (SuSFSManager.addSusPath(context, newPath.trim())) {
                                    susPaths = SuSFSManager.getSusPaths(context)
                                }
                                isLoading = false
                                newPath = ""
                                showAddPathDialog = false
                            }
                        }
                    },
                    enabled = newPath.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddPathDialog = false
                        newPath = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 添加挂载对话框
    if (showAddMountDialog) {
        AlertDialog(
            onDismissRequest = { showAddMountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_sus_mount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newMount,
                    onValueChange = { newMount = it },
                    label = { Text(stringResource(R.string.susfs_mount_path_label)) },
                    placeholder = { Text(stringResource(R.string.susfs_path_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newMount.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                if (SuSFSManager.addSusMount(context, newMount.trim())) {
                                    susMounts = SuSFSManager.getSusMounts(context)
                                }
                                isLoading = false
                                newMount = ""
                                showAddMountDialog = false
                            }
                        }
                    },
                    enabled = newMount.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddMountDialog = false
                        newMount = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 添加尝试卸载对话框
    if (showAddUmountDialog) {
        AlertDialog(
            onDismissRequest = { showAddUmountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_try_umount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newUmountPath,
                        onValueChange = { newUmountPath = it },
                        label = { Text(stringResource(R.string.susfs_path_label)) },
                        placeholder = { Text(stringResource(R.string.susfs_path_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = umountModeExpanded,
                        onExpandedChange = { umountModeExpanded = !umountModeExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (newUmountMode == 0)
                                stringResource(R.string.susfs_umount_mode_normal)
                            else
                                stringResource(R.string.susfs_umount_mode_detach),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(stringResource(R.string.susfs_umount_mode_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = umountModeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = umountModeExpanded,
                            onDismissRequest = { umountModeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.susfs_umount_mode_normal)) },
                                onClick = {
                                    newUmountMode = 0
                                    umountModeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.susfs_umount_mode_detach)) },
                                onClick = {
                                    newUmountMode = 1
                                    umountModeExpanded = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUmountPath.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                if (SuSFSManager.addTryUmount(context, newUmountPath.trim(), newUmountMode)) {
                                    tryUmounts = SuSFSManager.getTryUmounts(context)
                                }
                                isLoading = false
                                newUmountPath = ""
                                newUmountMode = 0
                                showAddUmountDialog = false
                            }
                        }
                    },
                    enabled = newUmountPath.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddUmountDialog = false
                        newUmountPath = ""
                        newUmountMode = 0
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 运行尝试卸载确认对话框
    if (showRunUmountDialog) {
        AlertDialog(
            onDismissRequest = { showRunUmountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_run_umount_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.susfs_run_umount_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            SuSFSManager.runTryUmount(context)
                            isLoading = false
                            showRunUmountDialog = false
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRunUmountDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 重置SUS路径确认对话框
    if (showResetPathsDialog) {
        AlertDialog(
            onDismissRequest = { showResetPathsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_paths_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.susfs_reset_paths_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            SuSFSManager.saveSusPaths(context, emptySet())
                            susPaths = emptySet()
                            if (SuSFSManager.isAutoStartEnabled(context)) {
                                SuSFSManager.configureAutoStart(context, true)
                            }
                            isLoading = false
                            showResetPathsDialog = false
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetPathsDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 重置SUS挂载确认对话框
    if (showResetMountsDialog) {
        AlertDialog(
            onDismissRequest = { showResetMountsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_mounts_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.susfs_reset_mounts_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            SuSFSManager.saveSusMounts(context, emptySet())
                            susMounts = emptySet()
                            if (SuSFSManager.isAutoStartEnabled(context)) {
                                SuSFSManager.configureAutoStart(context, true)
                            }
                            isLoading = false
                            showResetMountsDialog = false
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetMountsDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 重置尝试卸载确认对话框
    if (showResetUmountsDialog) {
        AlertDialog(
            onDismissRequest = { showResetUmountsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_umounts_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.susfs_reset_umounts_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            SuSFSManager.saveTryUmounts(context, emptySet())
                            tryUmounts = emptySet()
                            if (SuSFSManager.isAutoStartEnabled(context)) {
                                SuSFSManager.configureAutoStart(context, true)
                            }
                            isLoading = false
                            showResetUmountsDialog = false
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetUmountsDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 重置确认对话框
    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = {
                Text(
                    text = stringResource(R.string.susfs_reset_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmReset = false
                        coroutineScope.launch {
                            isLoading = true
                            if (SuSFSManager.resetToDefault(context)) {
                                unameValue = "default"
                                buildTimeValue = "default"
                                lastAppliedValue = "default"
                                lastAppliedBuildTime = "default"
                                autoStartEnabled = false
                            }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmReset = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 主界面布局
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.susfs_config_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navigator.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = CardConfig.cardAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = CardConfig.cardAlpha)
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        },
        bottomBar = {
            // 统一的底部按钮栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        SuSFSTab.BASIC_SETTINGS -> {
                            // 应用按钮
                            Button(
                                onClick = {
                                    if (unameValue.isNotBlank() || buildTimeValue.isNotBlank()) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            val finalUnameValue = unameValue.trim().ifBlank { "default" }
                                            val finalBuildTimeValue = buildTimeValue.trim().ifBlank { "default" }
                                            val success = SuSFSManager.setUname(context, finalUnameValue, finalBuildTimeValue)
                                            if (success) {
                                                lastAppliedValue = finalUnameValue
                                                lastAppliedBuildTime = finalBuildTimeValue
                                            }
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading && (unameValue.isNotBlank() || buildTimeValue.isNotBlank()),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Text(
                                    stringResource(R.string.susfs_apply),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 重置按钮
                            OutlinedButton(
                                onClick = { showConfirmReset = true },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.susfs_reset, stringResource(selectedTab.displayNameRes)),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SuSFSTab.SUS_PATHS -> {
                            // 重置按钮
                            OutlinedButton(
                                onClick = { showResetPathsDialog = true },
                                enabled = !isLoading && susPaths.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.susfs_reset, stringResource(selectedTab.displayNameRes)),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SuSFSTab.SUS_MOUNTS -> {
                            // 重置按钮
                            OutlinedButton(
                                onClick = { showResetMountsDialog = true },
                                enabled = !isLoading && susMounts.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.susfs_reset, stringResource(selectedTab.displayNameRes)),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SuSFSTab.TRY_UMOUNT -> {
                            // 重置按钮
                            OutlinedButton(
                                onClick = { showResetUmountsDialog = true },
                                enabled = !isLoading && tryUmounts.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.susfs_reset, stringResource(selectedTab.displayNameRes)),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SuSFSTab.PATH_SETTINGS -> {
                            // 重置按钮
                            OutlinedButton(
                                onClick = {
                                    androidDataPath = "/sdcard/Android/data"
                                    sdcardPath = "/sdcard"
                                    coroutineScope.launch {
                                        isLoading = true
                                        SuSFSManager.setAndroidDataPath(context, androidDataPath)
                                        SuSFSManager.setSdcardPath(context, sdcardPath)
                                        isLoading = false
                                    }
                                },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.susfs_reset, stringResource(selectedTab.displayNameRes)),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SuSFSTab.ENABLED_FEATURES -> {
                            // 刷新按钮（这个页面没有重置功能，所以显示刷新按钮）
                            Button(
                                onClick = { loadEnabledFeatures() },
                                enabled = !isLoadingFeatures,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.refresh),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // 标签页
            ScrollableTabRow(
                selectedTabIndex = allTabs.indexOf(selectedTab),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                allTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = stringResource(tab.displayNameRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 标签页内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    SuSFSTab.BASIC_SETTINGS -> {
                        BasicSettingsContent(
                            unameValue = unameValue,
                            onUnameValueChange = { unameValue = it },
                            buildTimeValue = buildTimeValue,
                            onBuildTimeValueChange = { buildTimeValue = it },
                            autoStartEnabled = autoStartEnabled,
                            canEnableAutoStart = canEnableAutoStart,
                            isLoading = isLoading,
                            onAutoStartToggle = { enabled ->
                                if (canEnableAutoStart) {
                                    coroutineScope.launch {
                                        isLoading = true
                                        if (SuSFSManager.configureAutoStart(context, enabled)) {
                                            autoStartEnabled = enabled
                                        }
                                        isLoading = false
                                    }
                                }
                            },
                            context = context
                        )
                    }
                    SuSFSTab.SUS_PATHS -> {
                        SusPathsContent(
                            susPaths = susPaths,
                            isLoading = isLoading,
                            onAddPath = { showAddPathDialog = true },
                            onRemovePath = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusPath(context, path)) {
                                        susPaths = SuSFSManager.getSusPaths(context)
                                    }
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.SUS_MOUNTS -> {
                        SusMountsContent(
                            susMounts = susMounts,
                            isLoading = isLoading,
                            onAddMount = { showAddMountDialog = true },
                            onRemoveMount = { mount ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusMount(context, mount)) {
                                        susMounts = SuSFSManager.getSusMounts(context)
                                    }
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.TRY_UMOUNT -> {
                        TryUmountContent(
                            tryUmounts = tryUmounts,
                            isLoading = isLoading,
                            onAddUmount = { showAddUmountDialog = true },
                            onRunUmount = { showRunUmountDialog = true },
                            onRemoveUmount = { umountEntry ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeTryUmount(context, umountEntry)) {
                                        tryUmounts = SuSFSManager.getTryUmounts(context)
                                    }
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.PATH_SETTINGS -> {
                        PathSettingsContent(
                            androidDataPath = androidDataPath,
                            onAndroidDataPathChange = { androidDataPath = it },
                            sdcardPath = sdcardPath,
                            onSdcardPathChange = { sdcardPath = it },
                            isLoading = isLoading,
                            onSetAndroidDataPath = {
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.setAndroidDataPath(context, androidDataPath.trim())
                                    isLoading = false
                                }
                            },
                            onSetSdcardPath = {
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.setSdcardPath(context, sdcardPath.trim())
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.ENABLED_FEATURES -> {
                        EnabledFeaturesContent(
                            enabledFeatures = enabledFeatures
                        )
                    }
                }
            }
        }
    }
}

/**
 * 基本设置内容组件
 */
@Composable
private fun BasicSettingsContent(
    unameValue: String,
    onUnameValueChange: (String) -> Unit,
    buildTimeValue: String,
    onBuildTimeValueChange: (String) -> Unit,
    autoStartEnabled: Boolean,
    canEnableAutoStart: Boolean,
    isLoading: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    context: android.content.Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.susfs_config_description),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.susfs_config_description_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        // Uname输入框
        OutlinedTextField(
            value = unameValue,
            onValueChange = onUnameValueChange,
            label = { Text(stringResource(R.string.susfs_uname_label)) },
            placeholder = { Text(stringResource(R.string.susfs_uname_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // 构建时间伪装输入框
        OutlinedTextField(
            value = buildTimeValue,
            onValueChange = onBuildTimeValueChange,
            label = { Text(stringResource(R.string.susfs_build_time_label)) },
            placeholder = { Text(stringResource(R.string.susfs_build_time_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // 当前值显示
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.susfs_current_value, SuSFSManager.getUnameValue(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.susfs_current_build_time, SuSFSManager.getBuildTimeValue(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 开机自启动开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (canEnableAutoStart) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = if (canEnableAutoStart) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.susfs_autostart_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (canEnableAutoStart) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (canEnableAutoStart) {
                            stringResource(R.string.susfs_autostart_description)
                        } else {
                            stringResource(R.string.susfs_autostart_requirement)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (canEnableAutoStart) 1f else 0.5f
                        ),
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartToggle,
                    enabled = !isLoading && canEnableAutoStart
                )
            }
        }
    }
}

/**
 * SUS路径内容组件
 */
@Composable
private fun SusPathsContent(
    susPaths: Set<String>,
    isLoading: Boolean,
    onAddPath: () -> Unit,
    onRemovePath: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UnifiedButtonRow(
            primaryButton = {
                FloatingActionButton(
                    onClick = onAddPath,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            secondaryButtons = {
                Text(
                    text = stringResource(R.string.susfs_sus_paths_management),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        )

        if (susPaths.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_paths_configured)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(susPaths.toList()) { path ->
                    PathItemCard(
                        path = path,
                        icon = Icons.Default.Folder,
                        onDelete = { onRemovePath(path) },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

/**
 * SUS挂载内容组件
 */
@Composable
private fun SusMountsContent(
    susMounts: Set<String>,
    isLoading: Boolean,
    onAddMount: () -> Unit,
    onRemoveMount: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UnifiedButtonRow(
            primaryButton = {
                FloatingActionButton(
                    onClick = onAddMount,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            secondaryButtons = {
                Text(
                    text = stringResource(R.string.susfs_sus_mounts_management),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        )

        if (susMounts.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_mounts_configured)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(susMounts.toList()) { mount ->
                    PathItemCard(
                        path = mount,
                        icon = Icons.Default.Storage,
                        onDelete = { onRemoveMount(mount) },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

/**
 * 尝试卸载内容组件
 */
@Composable
private fun TryUmountContent(
    tryUmounts: Set<String>,
    isLoading: Boolean,
    onAddUmount: () -> Unit,
    onRunUmount: () -> Unit,
    onRemoveUmount: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UnifiedButtonRow(
            primaryButton = {
                FloatingActionButton(
                    onClick = onAddUmount,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            secondaryButtons = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.susfs_try_umount_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (tryUmounts.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = onRunUmount,
                            modifier = Modifier.size(40.dp),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        )

        if (tryUmounts.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_umounts_configured)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tryUmounts.toList()) { umountEntry ->
                    val parts = umountEntry.split("|")
                    val path = if (parts.isNotEmpty()) parts[0] else umountEntry
                    val mode = if (parts.size > 1) parts[1] else "0"
                    val modeText = if (mode == "0")
                        stringResource(R.string.susfs_umount_mode_normal_short)
                    else
                        stringResource(R.string.susfs_umount_mode_detach_short)

                    PathItemCard(
                        path = path,
                        icon = Icons.Default.Storage,
                        additionalInfo = stringResource(R.string.susfs_umount_mode_display, modeText, mode),
                        onDelete = { onRemoveUmount(umountEntry) },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

/**
 * 路径设置内容组件
 */
@SuppressLint("SdCardPath")
@Composable
private fun PathSettingsContent(
    androidDataPath: String,
    onAndroidDataPathChange: (String) -> Unit,
    sdcardPath: String,
    onSdcardPathChange: (String) -> Unit,
    isLoading: Boolean,
    onSetAndroidDataPath: () -> Unit,
    onSetSdcardPath: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.susfs_path_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Android Data路径设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = androidDataPath,
                    onValueChange = onAndroidDataPathChange,
                    label = { Text(stringResource(R.string.susfs_android_data_path_label)) },
                    placeholder = { Text("/sdcard/Android/data") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = onSetAndroidDataPath,
                    enabled = !isLoading && androidDataPath.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_set_android_data_path))
                }
            }
        }

        // SD卡路径设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = sdcardPath,
                    onValueChange = onSdcardPathChange,
                    label = { Text(stringResource(R.string.susfs_sdcard_path_label)) },
                    placeholder = { Text("/sdcard") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = onSetSdcardPath,
                    enabled = !isLoading && sdcardPath.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_set_sdcard_path))
                }
            }
        }
    }
}

/**
 * 启用功能状态内容组件
 */
@Composable
private fun EnabledFeaturesContent(
    enabledFeatures: List<SuSFSManager.EnabledFeature>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.susfs_enabled_features_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.susfs_enabled_features_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (enabledFeatures.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_features_found)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(enabledFeatures) { feature ->
                    FeatureStatusCard(feature = feature)
                }
            }
        }
    }
}

/**
 * 统一的按钮布局组件
 */
@Composable
private fun UnifiedButtonRow(
    primaryButton: @Composable () -> Unit,
    secondaryButtons: @Composable () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            secondaryButtons()
        }
        primaryButton()
    }
}

/**
 * 空状态显示组件
 */
@Composable
private fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 路径项目卡片组件
 */
@Composable
private fun PathItemCard(
    path: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDelete: () -> Unit,
    isLoading: Boolean = false,
    additionalInfo: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (additionalInfo != null) {
                        Text(
                            text = additionalInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !isLoading,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 启用功能状态卡片组件
 */
@Composable
private fun FeatureStatusCard(
    feature: SuSFSManager.EnabledFeature,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    feature.isEnabled -> MaterialTheme.colorScheme.primary
                    else -> Color.Gray
                }
            ) {
                Text(
                    text = feature.statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        feature.isEnabled -> MaterialTheme.colorScheme.onPrimary
                        else -> Color.White
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}