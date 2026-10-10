package com.lingxi.scoretrack.ui.screen.paper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.data.file.DocumentExtractor
import com.lingxi.scoretrack.domain.model.DIFFICULTY_OPTIONS
import com.lingxi.scoretrack.domain.model.QuestionDraft
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatScore

/**
 * 手动录入一次考试中某一科目的小题分。
 *
 * 这是错题本的**手动入口** —— 在此之前，错题只能靠 AI 识别导入产生。
 */
@Composable
fun PaperEditScreen(
    examId: Long,
    subjectId: Long,
    onBack: () -> Unit,
    viewModel: PaperEditViewModel = hiltViewModel()
) {
    LaunchedEffect(examId, subjectId) { viewModel.init(examId, subjectId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 上传一份卷子（照片 / PDF / CSV / Excel），让 AI 把小题读出来
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importFrom)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = state.subjectName.ifBlank { "小题分" },
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = { filePicker.launch(DocumentExtractor.SUPPORTED_MIME_TYPES) },
                    enabled = !state.isExtracting && !state.isSaving
                ) {
                    Text(if (state.isExtracting) "提取中" else "导入")
                }
                TextButton(
                    onClick = { viewModel.save(onBack) },
                    enabled = state.canSave
                ) {
                    Text(if (state.isSaving) "保存中" else "保存")
                }
            }
        )

        if (state.isExtracting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(state)
            }

            state.error?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::dismissError) { Text("知道了") }
                        }
                    }
                }
            }

            itemsIndexed(state.questions, key = { _, draft -> draft.id }) { index, draft ->
                QuestionCard(
                    index = index,
                    draft = draft,
                    expanded = state.expandedIndex == index,
                    onToggle = { viewModel.toggleExpand(index) },
                    onUpdate = { transform -> viewModel.updateQuestion(index, transform) },
                    onRemove = { viewModel.removeQuestion(index) }
                )
            }

            item {
                OutlinedButton(
                    onClick = viewModel::addQuestion,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ 添加一题")
                }
            }

            item {
                Text(
                    text = "得分低于满分的题会自动进入错题本；改对之后会自动移出。\n" +
                        "也可以点右上角「导入」，上传卷子照片、PDF、CSV 或 Excel，" +
                        "AI 会按题号把小题填进来（同题号更新，新题号追加）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(state: PaperEditUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = state.examName.ifBlank { "未命名考试" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已录 ${state.questions.size} 题 · " +
                    "总分 ${formatScore(state.totalScore)}/${formatScore(state.totalFullScore)}" +
                    if (state.wrongCount > 0) " · 错题 ${state.wrongCount} 道" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCard(
    index: Int,
    draft: QuestionDraft,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: ((QuestionDraft) -> QuestionDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            // ---- 收起状态就能看全的关键信息 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "第 ${draft.number.ifBlank { "?" }} 题",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (draft.isWrong) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "错题",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatScore(draft.scoreValue)} / " +
                            "${formatScore(draft.fullScoreValue)} 分" +
                            (draft.difficulty?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.number,
                        onValueChange = { value -> onUpdate { it.copy(number = value) } },
                        label = { Text("题号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.score,
                        onValueChange = { value -> onUpdate { it.copy(score = value) } },
                        label = { Text("得分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.fullScore,
                        onValueChange = { value -> onUpdate { it.copy(fullScore = value) } },
                        label = { Text("满分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIFFICULTY_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = draft.difficulty == option,
                            onClick = {
                                onUpdate {
                                    it.copy(difficulty = if (it.difficulty == option) null else option)
                                }
                            },
                            label = { Text(option) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { value -> onUpdate { it.copy(content = value) } },
                    label = { Text("题干（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.userAnswer,
                    onValueChange = { value -> onUpdate { it.copy(userAnswer = value) } },
                    label = { Text("我的答案（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.answer,
                    onValueChange = { value -> onUpdate { it.copy(answer = value) } },
                    label = { Text("正确答案（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.wrongReason,
                    onValueChange = { value -> onUpdate { it.copy(wrongReason = value) } },
                    label = { Text("错因（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除这题", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}