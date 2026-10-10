package com.lingxi.scoretrack.ui.screen.exam

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatDate

/**
 * 新建 / 编辑一次考试，并一次性录入各科分数。
 * 保存走 ExamRepository.saveExamWithScores —— 考试与成绩在同一事务内落库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditScreen(
    examId: Long,
    onBack: () -> Unit,
    onOpenPaper: (subjectId: Long) -> Unit,
    viewModel: ExamEditViewModel = hiltViewModel()
) {
    LaunchedEffect(examId) { viewModel.init(examId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (state.isEditing) "编辑考试" else "新建考试",
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = { viewModel.save(onBack) },
                    enabled = state.canSave && !state.isSaving
                ) {
                    Text(if (state.isSaving) "保存中" else "保存")
                }
            }
        )

        if (state.isLoading) {
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
                    ExamInfoCard(
                        state = state,
                        onNameChange = viewModel::updateName,
                        onTypeChange = viewModel::updateType,
                        onDateClick = { showDatePicker = true },
                        onRemarkChange = viewModel::updateRemark
                    )
                }

                item {
                    Text(
                        text = "各科成绩（已填 ${state.filledCount} 科）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                itemsIndexed(state.entries, key = { _, e -> e.subjectId }) { index, entry ->
                    ScoreEntryCard(
                        entry = entry,
                        // 新建考试时还没有 examId，试卷无从挂靠
                        canOpenPaper = state.isEditing,
                        onOpenPaper = { onOpenPaper(entry.subjectId) },
                        onScoreChange = { value ->
                            viewModel.updateEntry(index) { it.copy(score = value) }
                        },
                        onFullScoreChange = { value ->
                            viewModel.updateEntry(index) { it.copy(fullScore = value) }
                        },
                        onClassRankChange = { value ->
                            viewModel.updateEntry(index) { it.copy(classRank = value) }
                        },
                        onGradeRankChange = { value ->
                            viewModel.updateEntry(index) { it.copy(gradeRank = value) }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { viewModel.updateDate(it) }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamInfoCard(
    state: ExamEditUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onRemarkChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("考试名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExamEditUiState.EXAM_TYPES.take(4).forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            label = { Text(type) }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = formatDate(state.date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("日期") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onDateClick) {
                    Text("改")
                }
            }

            OutlinedTextField(
                value = state.remark,
                onValueChange = onRemarkChange,
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScoreEntryCard(
    entry: ScoreEntryInput,
    canOpenPaper: Boolean,
    onOpenPaper: () -> Unit,
    onScoreChange: (String) -> Unit,
    onFullScoreChange: (String) -> Unit,
    onClassRankChange: (String) -> Unit,
    onGradeRankChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (canOpenPaper) {
                    TextButton(onClick = onOpenPaper) {
                        Text("录小题分 ›")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = entry.score,
                    onValueChange = onScoreChange,
                    label = { Text("得分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = entry.fullScore,
                    onValueChange = onFullScoreChange,
                    label = { Text("满分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = entry.classRank,
                    onValueChange = onClassRankChange,
                    label = { Text("班排") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = entry.gradeRank,
                    onValueChange = onGradeRankChange,
                    label = { Text("级排") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}