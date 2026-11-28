package com.lingxi.scoretrack.ui.screen.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.lingxi.scoretrack.domain.exam.ExamTypeCatalog
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatDate

/**
 * 新建 / 编辑一次考试，并一次性录入各科分数。
 * 保存走 ExamRepository.saveExamWithScores —— 考试与成绩在同一事务内落库。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showTypeDialog by remember { mutableStateOf(false) }
    val typeMessage by viewModel.typeMessage.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (state.isEditing) "编辑考试" else "新建考试",
            // 顺手返回时把反思存下来：写下的东西不该因为忘点保存键就没了
            onBack = {
                viewModel.saveSilently()
                onBack()
            },
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
                        onManageTypes = { showTypeDialog = true },
                        onDateClick = { showDatePicker = true },
                        onRemarkChange = viewModel::updateRemark
                    )
                }
                item {
                    ReflectionCard(
                        reflection = state.reflection,
                        aiReflection = state.aiReflection,
                        aiSaved = state.aiReflectionSaved,
                        isReflecting = state.isReflecting,
                        error = state.reflectError,
                        onReflectionChange = viewModel::updateReflection,
                        onGenerate = viewModel::generateReflection,
                        onDismissError = viewModel::dismissReflectError
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

    if (showTypeDialog) {
        ExamTypeDialog(
            types = state.examTypes,
            message = typeMessage,
            onAdd = viewModel::addExamType,
            onRemove = viewModel::removeExamType,
            onDismissMessage = viewModel::dismissTypeMessage,
            onDismiss = { showTypeDialog = false }
        )
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
    onManageTypes: () -> Unit,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 全部类型都摆出来，不再 take(4) —— 七个默认类型只露四个，
                    // 用户根本不知道「随堂」「其他」也在里面
                    state.examTypes.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            label = { Text(type) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onManageTypes,
                        label = { Text("自定义") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
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

/**
 * 成绩反思 + AI 复盘。
 *
 * 单独成一张卡，而不是塞进「考试信息」里：反思是主观内容，通常写好几行，
 * 和上面名字、日期那几个单行输入框不是一类东西。
 */
@Composable
private fun ReflectionCard(
    reflection: String,
    aiReflection: String?,
    aiSaved: Boolean,
    isReflecting: Boolean,
    error: String?,
    onReflectionChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "成绩反思",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "这次为什么考成这样？粗心、时间不够、还是某个知识点没吃透 —— " +
                        "写下来，AI 才能顺着你的思路给建议。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = reflection,
                onValueChange = onReflectionChange,
                placeholder = { Text("例：数学最后两道大题没时间做，选择错了 3 道") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            FilledTonalButton(
                onClick = onGenerate,
                enabled = !isReflecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isReflecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在分析…")
                } else {
                    Text(if (aiReflection.isNullOrBlank()) "让 AI 帮我分析" else "重新分析")
                }
            }

            error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) { Text("知道了") }
                    }
                }
            }

            aiReflection?.takeIf { it.isNotBlank() }?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (aiSaved) "AI 的建议 · 已保存" else "AI 的建议",
                            style = MaterialTheme.typography.labelLarge
                        )
                        // 长按可选中复制 —— 用户常想把它粘到笔记本里
                        SelectionContainer {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
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

/**
 * 自定义考试类型管理。
 *
 * 只允许删「自己加的」—— 默认那 7 种是常量，删了下次启动也会回来，
 * 与其给一个假开关，不如直接不给。
 */
@Composable
private fun ExamTypeDialog(
    types: List<String>,
    message: String?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val custom = remember(types) {
        types.filterNot { it in ExamTypeCatalog.DEFAULT_TYPES }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义考试类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "默认类型：" + ExamTypeCatalog.DEFAULT_TYPES.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("新类型名") },
                        placeholder = { Text("例如 单元测") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onAdd(input)
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Text("添加")
                    }
                }

                if (custom.isNotEmpty()) {
                    Text(
                        text = "你添加的",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    custom.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(name) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "删除 " + name,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                message?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismissMessage()
                onDismiss()
            }) { Text("完成") }
        }
    )
}
