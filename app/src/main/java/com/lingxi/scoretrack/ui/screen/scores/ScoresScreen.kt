package com.lingxi.scoretrack.ui.screen.scores

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.components.EmptyState
import com.lingxi.scoretrack.ui.util.formatDate
import com.lingxi.scoretrack.ui.util.formatScore
import com.lingxi.scoretrack.ui.util.scoreColorHex
import com.lingxi.scoretrack.ui.util.scoreRate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lingxi.scoretrack.data.repository.ExamRepository

@Composable
fun ScoresScreen(
    onCreateExam: () -> Unit,
    onEditExam: (Long) -> Unit,
    onImportSheet: () -> Unit,
    viewModel: ScoresViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val deleteCascade by viewModel.deleteCascade.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "成绩",
            actions = {
                TextButton(onClick = onImportSheet) {
                    Text("识别导入")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.isEmpty -> {
                    EmptyExams(onCreateExam = onCreateExam)
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.exams, key = { it.id }) { exam ->
                            ExamCard(
                                exam = exam,
                                total = state.totalOf(exam.id),
                                fullTotal = state.fullTotalOf(exam.id),
                                subjectCount = state.scoresByExam[exam.id]?.size ?: 0,
                                onClick = { onEditExam(exam.id) },
                                onDelete = { viewModel.requestDelete(exam) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    // FAB 放在外层 Box 的相对位置
    Box(modifier = Modifier.fillMaxSize()) {
        ExtendedFloatingActionButton(
            onClick = onCreateExam,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新建考试") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
    // 删除确认框挂在最外层，避免被 FAB 那个 Box 的层级盖住
    deleteTarget?.let { target ->
        DeleteExamDialog(
            target = target,
            cascade = deleteCascade,
            onCancel = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete
        )
    }
}

@Composable
private fun EmptyExams(onCreateExam: () -> Unit) {
    // onCreateExam 一直是这个函数的参数，但之前从没被用过 ——
    // 空状态里正好是放主行动按钮的位置，顺手接上。
    EmptyState(
        icon = Icons.Filled.List,
        title = "还没有考试记录",
        description = "一次性录入各科分数，之后就能看趋势、比目标、出诊断报告。",
        action = {
            Button(onClick = onCreateExam) {
                Text("新建考试")
            }
        }
    )
}

/**
 * 考试卡片。
 *
 * 「更多」菜单放在卡片右侧而不是用长按：长按没有任何视觉提示，
 * 用户根本不会去试 —— 等于没做。
 */
@Composable
private fun ExamCard(
    exam: ExamEntity,
    total: Float,
    fullTotal: Float,
    subjectCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val rate = scoreRate(total, fullTotal)
    val rateColor = Color(scoreColorHex(rate))
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            // end 只有 4dp：把右边留给菜单图标自己的 48dp 触摸区域，
            // 视觉上仍然和上面的 title 对齐
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${formatDate(exam.date)} · ${exam.type} · $subjectCount 科",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (fullTotal > 0f) {
                Text(
                    text = formatScore(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = rateColor
                )
                Text(
                    text = "/ ${formatScore(fullTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}

/**
 * 删除考试的二次确认。
 *
 * 这里特意多花几行字解释「会连带删掉什么」，因为 exam → paper → question
 * → mistake 这条链在界面上**完全不可见**。用户以为只是不记这场考试了，
 * 实际上可能一起删掉十几条错题记录 —— 事后才发现就晚了。
 */
@Composable
private fun DeleteExamDialog(
    target: ExamEntity,
    cascade: ExamRepository.ExamCascadeCount?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("删除这场考试？") },
        text = {
            Column {
                Text(
                    text = "「${target.name}」及其各科成绩会被永久删除，无法恢复。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (val c = cascade) {
                    null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在检查关联数据…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> if (c.hasAttachments) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "这场考试下面还有这些数据，会一并删除：",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val body = MaterialTheme.colorScheme.onErrorContainer
                                if (c.papers > 0) {
                                    Text(
                                        text = "· ${c.papers} 份试卷",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = body
                                    )
                                }
                                if (c.questions > 0) {
                                    Text(
                                        text = "· ${c.questions} 道题目",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = body
                                    )
                                }
                                if (c.mistakes > 0) {
                                    Text(
                                        text = "· ${c.mistakes} 条错题记录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = body
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "这场考试下面没有试卷或错题，删除不会波及其它数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("永久删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}
