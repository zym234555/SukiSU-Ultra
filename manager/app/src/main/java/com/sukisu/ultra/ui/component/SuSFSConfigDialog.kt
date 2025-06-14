package com.sukisu.ultra.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.SuSFSManager
import kotlinx.coroutines.launch

/**
 * SuSFS配置对话框
 */
@SuppressLint("SdCardPath")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuSFSConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
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

    val tabTitles = listOf(
        stringResource(R.string.susfs_tab_basic_settings),
        stringResource(R.string.susfs_tab_sus_paths),
        stringResource(R.string.susfs_tab_sus_mounts),
        stringResource(R.string.susfs_tab_try_umount),
        stringResource(R.string.susfs_tab_path_settings),
        stringResource(R.string.susfs_tab_enabled_features)
    )

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
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 5) {
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

    // 添加路径对话框
    if (showAddPathDialog) {
        AlertDialog(
            onDismissRequest = { showAddPathDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_sus_path),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 添加挂载对话框
    if (showAddMountDialog) {
        AlertDialog(
            onDismissRequest = { showAddMountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_sus_mount),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 添加尝试卸载对话框
    if (showAddUmountDialog) {
        AlertDialog(
            onDismissRequest = { showAddUmountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_add_try_umount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 运行尝试卸载确认对话框
    if (showRunUmountDialog) {
        AlertDialog(
            onDismissRequest = { showRunUmountDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_run_umount_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 重置SUS路径确认对话框
    if (showResetPathsDialog) {
        AlertDialog(
            onDismissRequest = { showResetPathsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_paths_title),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 重置SUS挂载确认对话框
    if (showResetMountsDialog) {
        AlertDialog(
            onDismissRequest = { showResetMountsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_mounts_title),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 重置尝试卸载确认对话框
    if (showResetUmountsDialog) {
        AlertDialog(
            onDismissRequest = { showResetUmountsDialog = false },
            title = {
                Text(
                    stringResource(R.string.susfs_reset_umounts_title),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 重置确认对话框
    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = {
                Text(
                    text = stringResource(R.string.susfs_reset_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
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
            shape = RoundedCornerShape(16.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.susfs_config_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 优化后的标签页
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 标签页内容
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            // 基本设置
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
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.susfs_config_description),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.susfs_config_description_text),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                // Uname输入框
                                OutlinedTextField(
                                    value = unameValue,
                                    onValueChange = { unameValue = it },
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
                                    onValueChange = { buildTimeValue = it },
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
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.susfs_current_build_time, SuSFSManager.getBuildTimeValue(context)),
                                        style = MaterialTheme.typography.bodySmall,
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
                                            .padding(14.dp),
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
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.susfs_autostart_title),
                                                    style = MaterialTheme.typography.titleSmall,
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
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = if (canEnableAutoStart) 1f else 0.5f
                                                ),
                                                lineHeight = 14.sp
                                            )
                                        }
                                        Switch(
                                            checked = autoStartEnabled,
                                            onCheckedChange = { enabled ->
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
                                            enabled = !isLoading && canEnableAutoStart
                                        )
                                    }
                                }

                                // 重置按钮
                                ResetInfoCard(
                                    tabName = stringResource(R.string.susfs_tab_basic_settings),
                                    onResetClick = { showConfirmReset = true },
                                    enabled = !isLoading
                                )
                            }
                        }
                        1 -> {
                            // SUS路径
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnifiedButtonRow(
                                    primaryButton = {
                                        FloatingActionButton(
                                            onClick = { showAddPathDialog = true },
                                            modifier = Modifier.size(48.dp),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.susfs_add_button_description),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    secondaryButtons = {
                                        Text(
                                            text = stringResource(R.string.susfs_sus_paths_management),
                                            style = MaterialTheme.typography.titleMedium,
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
                                        modifier = Modifier.height(180.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(susPaths.toList()) { path ->
                                            PathItemCard(
                                                path = path,
                                                icon = Icons.Default.Folder,
                                                onDelete = {
                                                    coroutineScope.launch {
                                                        isLoading = true
                                                        if (SuSFSManager.removeSusPath(context, path)) {
                                                            susPaths = SuSFSManager.getSusPaths(context)
                                                        }
                                                        isLoading = false
                                                    }
                                                },
                                                isLoading = isLoading
                                            )
                                        }
                                    }
                                }

                                ResetInfoCard(
                                    tabName = stringResource(R.string.susfs_tab_sus_paths),
                                    onResetClick = { showResetPathsDialog = true },
                                    enabled = !isLoading && susPaths.isNotEmpty()
                                )
                            }
                        }
                        2 -> {
                            // SUS挂载
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnifiedButtonRow(
                                    primaryButton = {
                                        FloatingActionButton(
                                            onClick = { showAddMountDialog = true },
                                            modifier = Modifier.size(48.dp),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.susfs_add_button_description),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    secondaryButtons = {
                                        Text(
                                            text = stringResource(R.string.susfs_sus_mounts_management),
                                            style = MaterialTheme.typography.titleMedium,
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
                                        modifier = Modifier.height(180.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(susMounts.toList()) { mount ->
                                            PathItemCard(
                                                path = mount,
                                                icon = Icons.Default.Storage,
                                                onDelete = {
                                                    coroutineScope.launch {
                                                        isLoading = true
                                                        if (SuSFSManager.removeSusMount(context, mount)) {
                                                            susMounts = SuSFSManager.getSusMounts(context)
                                                        }
                                                        isLoading = false
                                                    }
                                                },
                                                isLoading = isLoading
                                            )
                                        }
                                    }
                                }

                                ResetInfoCard(
                                    tabName = stringResource(R.string.susfs_tab_sus_mounts),
                                    onResetClick = { showResetMountsDialog = true },
                                    enabled = !isLoading && susMounts.isNotEmpty()
                                )
                            }
                        }
                        3 -> {
                            // 尝试卸载
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnifiedButtonRow(
                                    primaryButton = {
                                        FloatingActionButton(
                                            onClick = { showAddUmountDialog = true },
                                            modifier = Modifier.size(48.dp),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.susfs_add_button_description),
                                                modifier = Modifier.size(24.dp)
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
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (tryUmounts.isNotEmpty()) {
                                                FloatingActionButton(
                                                    onClick = { showRunUmountDialog = true },
                                                    modifier = Modifier.size(40.dp),
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = stringResource(R.string.susfs_run_button_description),
                                                        modifier = Modifier.size(20.dp)
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
                                        modifier = Modifier.height(180.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                onDelete = {
                                                    coroutineScope.launch {
                                                        isLoading = true
                                                        if (SuSFSManager.removeTryUmount(context, umountEntry)) {
                                                            tryUmounts = SuSFSManager.getTryUmounts(context)
                                                        }
                                                        isLoading = false
                                                    }
                                                },
                                                isLoading = isLoading
                                            )
                                        }
                                    }
                                }

                                ResetInfoCard(
                                    tabName = stringResource(R.string.susfs_tab_try_umount),
                                    onResetClick = { showResetUmountsDialog = true },
                                    enabled = !isLoading && tryUmounts.isNotEmpty()
                                )
                            }
                        }
                        4 -> {
                            // 路径设置
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.susfs_path_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Android Data路径设置
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = androidDataPath,
                                            onValueChange = { androidDataPath = it },
                                            label = { Text(stringResource(R.string.susfs_android_data_path_label)) },
                                            placeholder = { Text("/sdcard/Android/data") },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isLoading,
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isLoading = true
                                                    SuSFSManager.setAndroidDataPath(context, androidDataPath.trim())
                                                    isLoading = false
                                                }
                                            },
                                            enabled = !isLoading && androidDataPath.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth(),
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
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = sdcardPath,
                                            onValueChange = { sdcardPath = it },
                                            label = { Text(stringResource(R.string.susfs_sdcard_path_label)) },
                                            placeholder = { Text("/sdcard") },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isLoading,
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isLoading = true
                                                    SuSFSManager.setSdcardPath(context, sdcardPath.trim())
                                                    isLoading = false
                                                }
                                            },
                                            enabled = !isLoading && sdcardPath.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.susfs_set_sdcard_path))
                                        }
                                    }
                                }

                                ResetInfoCard(
                                    tabName = stringResource(R.string.susfs_tab_path_settings),
                                    onResetClick = {
                                        androidDataPath = "/sdcard/Android/data"
                                        sdcardPath = "/sdcard"
                                        coroutineScope.launch {
                                            isLoading = true
                                            SuSFSManager.setAndroidDataPath(context, androidDataPath)
                                            SuSFSManager.setSdcardPath(context, sdcardPath)
                                            isLoading = false
                                        }
                                    },
                                    enabled = !isLoading
                                )
                            }
                        }
                        5 -> {
                            // 启用功能状态
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnifiedButtonRow(
                                    primaryButton = {
                                        FloatingActionButton(
                                            onClick = { loadEnabledFeatures() },
                                            modifier = Modifier.size(48.dp),
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.susfs_refresh_button_description),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    secondaryButtons = {
                                        Text(
                                            text = stringResource(R.string.susfs_enabled_features_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .padding(end = 8.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.susfs_enabled_features_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                if (isLoadingFeatures) {
                                    EmptyStateCard(
                                        message = stringResource(R.string.refresh)
                                    )
                                } else if (enabledFeatures.isEmpty()) {
                                    EmptyStateCard(
                                        message = stringResource(R.string.susfs_no_features_found)
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.height(240.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(enabledFeatures) { feature ->
                                            FeatureStatusCard(feature = feature)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                if (selectedTabIndex == 0) {
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
                                        onDismiss()
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && (unameValue.isNotBlank() || buildTimeValue.isNotBlank()),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.susfs_apply))
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.susfs_done))
                    }
                }
            }
        },
        dismissButton = null,
        shape = RoundedCornerShape(20.dp)
    )
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
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            secondaryButtons()
        }
        primaryButton()
    }
}

/**
 * 重置功能说明卡片组件
 */
@Composable
private fun ResetInfoCard(
    tabName: String,
    onResetClick: () -> Unit,
    enabled: Boolean = true,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = stringResource(R.string.susfs_reset_section_description),
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.susfs_reset, tabName))
                }
            }
        }
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
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
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
            .padding(vertical = 2.dp),
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
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (additionalInfo != null) {
                        Text(
                            text = additionalInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.susfs_delete_button_description),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
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
            .padding(vertical = 2.dp),
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
                Text(
                    text = feature.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when {
                    feature.isEnabled -> MaterialTheme.colorScheme.primary
                    else -> Color.Gray
                },
                modifier = Modifier
            ) {
                Text(
                    text = feature.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}