package com.lingxi.scoretrack.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.ui.components.AppTopBar

@Composable
fun SettingsScreen(
    onOpenAiConfig: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val currentId by viewModel.currentProfileId.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "设置")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("AI") }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "AI 配置",
                        subtitle = "文本模型 / 视觉模型、API Key 与连通性测试",
                        onClick = onOpenAiConfig
                    )
                }
            }

            item { SectionTitle("档案") }
            item {
                SettingsCard {
                    if (profiles.isEmpty()) {
                        SettingsRow(title = "暂无档案", subtitle = "先新建一个吧", enabled = false)
                    } else {
                        profiles.forEachIndexed { index, profile ->
                            ProfileRow(
                                profile = profile,
                                selected = profile.id == currentId,
                                onClick = { viewModel.switchProfile(profile) },
                                onDelete = { viewModel.requestDelete(profile) }
                            )
                            if (index != profiles.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "新建档案",
                        subtitle = "会自动预置 9 门科目",
                        onClick = { showCreateDialog = true }
                    )
                }
            }

            item { SectionTitle("数据") }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "导入成绩单",
                        subtitle = "拍照识别，自动录入分数与错题",
                        onClick = onOpenImport
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "导出数据",
                        subtitle = "导出为文件备份 —— 开发中",
                        enabled = false
                    )
                }
            }

            item { SectionTitle("关于") }
            item {
                SettingsCard {
                    SettingsRow(title = "学迹 ScoreTrack", subtitle = "版本 0.1.0（开发中）", enabled = false)
                    HorizontalDivider()
                    SettingsRow(title = "本地优先", subtitle = "所有数据仅保存在本机", enabled = false)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建档案") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("档案名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProfile(name)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
            TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
        }
    )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除档案") },
            text = {
                Text(
                    "「${target.name}」及其全部数据（科目、考试、成绩、错题、分析报告等）" +
                        "将被永久删除，且无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (profile.isDefault) "默认档案" else "点击切换为当前档案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除档案",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}