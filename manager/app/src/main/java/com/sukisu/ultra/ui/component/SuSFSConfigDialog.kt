package com.sukisu.ultra.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.SuSFSManager
import kotlinx.coroutines.launch

/**
 * SuSFS配置对话框
 */
@Composable
fun SuSFSConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var unameValue by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmReset by remember { mutableStateOf(false) }
    var autoStartEnabled by remember { mutableStateOf(false) }
    var lastAppliedValue by remember { mutableStateOf("") }

    // 实时判断是否可以启用开机自启动
    val canEnableAutoStart by remember {
        derivedStateOf {
            unameValue.trim().isNotBlank() && unameValue.trim() != "default"
        }
    }

    // 加载当前配置
    LaunchedEffect(Unit) {
        unameValue = SuSFSManager.getUnameValue(context)
        autoStartEnabled = SuSFSManager.isAutoStartEnabled(context)
        lastAppliedValue = SuSFSManager.getLastAppliedValue(context)
    }

    // 当输入值变化时，自动调整开机自启动状态
    LaunchedEffect(canEnableAutoStart) {
        if (!canEnableAutoStart && autoStartEnabled) {
            // 如果输入值变为default或空，自动关闭开机自启动
            autoStartEnabled = false
            SuSFSManager.configureAutoStart(context, false)
        }
    }

    // 重置确认对话框
    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = {
                Text(
                    text = stringResource(R.string.susfs_reset_confirm_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(stringResource(R.string.susfs_reset_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmReset = false
                        coroutineScope.launch {
                            isLoading = true
                            if (SuSFSManager.resetToDefault(context)) {
                                unameValue = "default"
                                lastAppliedValue = "default"
                                autoStartEnabled = false
                            }
                            isLoading = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.susfs_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmReset = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
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
                // 说明卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.susfs_config_description),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.susfs_config_description_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 输入框
                OutlinedTextField(
                    value = unameValue,
                    onValueChange = { unameValue = it },
                    label = { Text(stringResource(R.string.susfs_uname_label)) },
                    placeholder = { Text(stringResource(R.string.susfs_uname_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 当前值显示
                Text(
                    text = stringResource(R.string.susfs_current_value, SuSFSManager.getUnameValue(context)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                    modifier = Modifier.padding(end = 8.dp)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (canEnableAutoStart) {
                                    stringResource(R.string.susfs_autostart_description)
                                } else {
                                    stringResource(R.string.susfs_autostart_tis)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (canEnableAutoStart) 1f else 0.5f
                                )
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

                Spacer(modifier = Modifier.height(16.dp))

                // 重置按钮
                OutlinedButton(
                    onClick = { showConfirmReset = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.susfs_reset_to_default))
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        if (unameValue.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                val success = SuSFSManager.setUname(context, unameValue.trim())
                                if (success) {
                                    lastAppliedValue = unameValue.trim()
                                    onDismiss()
                                }
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && unameValue.isNotBlank()
                ) {
                    Text(
                        stringResource(R.string.susfs_apply)
                    )
                }
            }
        },
        dismissButton = null
    )
}