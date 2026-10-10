package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatScore

private val REVIEW_STATUSES = listOf("未复习", "复习中", "已掌握")

@Composable
fun MistakeDetailScreen(
    mistakeId: Long,
    onBack: () -> Unit,
    viewModel: MistakeDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(mistakeId) { viewModel.init(mistakeId) }

    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPointDialog by remember { mutableStateOf(false) }
    var pointName by remember { mutableStateOf("") }

    // 详情就绪后把题目绑给 ViewModel —— 归类要落到具体哪道题上
    LaunchedEffect(detail?.questionId, detail?.subjectId) {
        val questionId = detail?.questionId
        val subjectId = detail?.subjectId
        if (questionId != null && subjectId != null) {
            viewModel.bindQuestion(questionId, subjectId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = detail?.let { "${it.number} 题" } ?: "错题详情",
            onBack = onBack,
            actions = {
                if (detail != null) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "移出错题本",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        val row = detail
        if (row == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionCard(title = "题目") {
                        Text(
                            text = row.content?.takeIf { it.isNotBlank() } ?: "（题目内容待补充）",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append(row.examName)
                                append(" · ")
                                append(row.paperTitle)
                                append(" · 得分 ")
                                append(formatScore(row.score))
                                append(" / ")
                                append(formatScore(row.fullScore))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SectionCard(title = "答案对照") {
                        LabeledBlock(
                            label = "我的答案",
                            value = row.userAnswer,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LabeledBlock(
                            label = "正确答案",
                            value = row.answer,
                            color = Color(0xFF22C55E)
                        )
                    }
                }

                if (!row.wrongReason.isNullOrBlank() || !row.note.isNullOrBlank()) {
                    item {
                        SectionCard(title = "错因与备注") {
                            row.wrongReason?.takeIf { it.isNotBlank() }?.let {
                                LabeledBlock(
                                    label = "错因",
                                    value = it,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            row.note?.takeIf { it.isNotBlank() }?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                LabeledBlock(
                                    label = "我的备注",
                                    value = it,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "知识点") {
                        if (points.isEmpty()) {
                            Text(
                                text = "还没有归类。挂上知识点，以后就能按薄弱点成组看题。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            points.forEach { point ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = point.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.removePoint(point.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "取消归类",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            pointName = ""
                            showPointDialog = true
                        }) {
                            Text("+ 添加知识点")
                        }
                    }
                }

                item {
                    SectionCard(title = "复习状态") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            REVIEW_STATUSES.forEach { status ->
                                FilterChip(
                                    selected = row.reviewStatus == status,
                                    onClick = { viewModel.setStatus(status) },
                                    label = { Text(status) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append("已复习 ")
                                append(row.reviewCount)
                                append(" 次")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.setStatus("已掌握") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !row.isMastered
                    ) {
                        Text(if (row.isMastered) "已掌握 ✓" else "标记为已掌握")
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("移出错题本") },
            text = { Text("这道题及它的复习记录会被删除，题目本身不受影响。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showPointDialog) {
        AlertDialog(
            onDismissRequest = { showPointDialog = false },
            title = { Text("添加知识点") },
            text = {
                Column {
                    Text(
                        text = "输入名称即可。库里没有的话会自动建一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = { pointName = it },
                        label = { Text("知识点名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addPoint(pointName)
                        showPointDialog = false
                    },
                    enabled = pointName.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPointDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledBlock(
    label: String,
    value: String?,
    color: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "（未填写）",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}