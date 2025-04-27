package com.sukisu.ultra.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.theme.ThemeConfig
import com.sukisu.ultra.ui.theme.getCardColors
import com.sukisu.ultra.ui.theme.getCardElevation

/**
 * 槽位选择对话框组件
 * 用于HorizonKernel刷写时选择目标槽位
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotSelectionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSlotSelected: (String) -> Unit
) {
    if (show) {
        val cardColor = if (!ThemeConfig.useDynamicColor) {
            ThemeConfig.currentTheme.ButtonContrast
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(id = R.string.select_slot_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.select_slot_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { onSlotSelected("a") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(id = R.string.slot_a),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = { onSlotSelected("b") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(id = R.string.slot_b),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            containerColor = getCardColors(cardColor.copy(alpha = 0.9f)).containerColor.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = getCardElevation()
        )
    }
}
