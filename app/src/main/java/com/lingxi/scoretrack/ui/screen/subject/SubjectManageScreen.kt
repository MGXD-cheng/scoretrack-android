package com.lingxi.scoretrack.ui.screen.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.SubjectColorPalette
import com.lingxi.scoretrack.ui.util.parseHexColor
import java.util.Locale

/**
 * 科目设置页。
 *
 * 科目本身是「每档案一套」的（subject.profileId），这里只是把一直被写死
 * 的预置 9 科变成可编辑 —— 名字、满分、颜色、顺序都能改。
 *
 * 所有操作只作用在当前档案上，页面顶部会明确写出是哪个档案，
 * 避免用户在 A 档案里改科目、以为改的是全局。
 */
@Composable
fun SubjectManageScreen(
    onBack: () -> Unit,
    viewModel: SubjectManageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val deleteScoreCount by viewModel.deleteScoreCount.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SubjectEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "科目设置", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (state.profileName.isBlank()) {
                            "还没有档案，先回设置里建一个"
                        } else {
                            "当前档案：${state.profileName}。这里的科目只影响这个档案，切到别的档案看到的是它自己的一套。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (state.isEmpty) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "这个档案还没有科目",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "点右下角的加号自己加，或者把默认的 9 科一键补回来。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column {
                                state.subjects.forEachIndexed { index, subject ->
                                    SubjectRow(
                                        subject = subject,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < state.subjects.lastIndex,
                                        onEdit = {
                                            editing = subject
                                            showEditor = true
                                        },
                                        onMoveUp = { viewModel.move(subject, -1) },
                                        onMoveDown = { viewModel.move(subject, 1) },
                                        onDelete = { viewModel.requestDelete(subject) }
                                    )
                                    if (index != state.subjects.lastIndex) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = viewModel::addMissingDefaults,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("补回默认科目（语数英物化生政史地）")
                    }
                }
            }
        }

        if (state.profileId > 0L) {
            FloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "新增科目")
            }
        }
    }

    if (showEditor) {
        SubjectEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onConfirm = { name, fullScore, color ->
                viewModel.save(editing, name, fullScore, color)
                showEditor = false
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除科目") },
            text = {
                Text(
                    if (deleteScoreCount > 0) {
                        "「${target.name}」下有 $deleteScoreCount 条成绩记录，删除科目会把它们一起删掉，且无法恢复。"
                    } else {
                        "删除「${target.name}」？这个科目下还没有成绩，删了不会丢数据。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMessage() }) { Text("知道了") }
            },
            text = { Text(text) }
        )
    }
}

@Composable
private fun SubjectRow(
    subject: SubjectEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 色点 —— 之前 color 字段存了却没人用，现在至少能一眼区分科目
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(parseHexColor(subject.color), CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "满分 ${formatScore(subject.fullScore)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "上移",
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "下移",
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除科目",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SubjectEditorDialog(
    initial: SubjectEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, fullScore: String, color: String) -> Unit
) {
    // remember(initial)：从「编辑 A」切到「编辑 B」时要重置输入框
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var fullScore by remember(initial) {
        mutableStateOf(initial?.let { formatScore(it.fullScore) } ?: "150")
    }
    var color by remember(initial) {
        mutableStateOf(initial?.color ?: SubjectColorPalette.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增科目" else "编辑科目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("科目名称") },
                    placeholder = { Text("例如 数学") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullScore,
                    onValueChange = { fullScore = it },
                    label = { Text("满分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("语数英通常是 150，其他 100") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SubjectColorPalette) { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(parseHexColor(hex), CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, fullScore, color) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 150.0 → "150"；112.5 → "112.5" */
private fun formatScore(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)