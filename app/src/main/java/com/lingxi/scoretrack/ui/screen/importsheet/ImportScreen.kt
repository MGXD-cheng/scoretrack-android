package com.lingxi.scoretrack.ui.screen.importsheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lingxi.scoretrack.data.file.DocumentExtractor
import com.lingxi.scoretrack.util.CameraCapture
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.domain.model.RecognizedExam
import com.lingxi.scoretrack.domain.model.RecognizedSubject
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatScore

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onOpenExam: (Long) -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }

    // OpenDocument 能让用户访问到「文件」而不只是「图片库」
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::onSourcePicked)
    }

    // ---------- 拍照 ----------

    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) {
            viewModel.onSourcePicked(uri)
        } else if (!saved) {
            cameraError = "拍照已取消"
        }
    }

    val launchCamera: () -> Unit = {
        runCatching {
            val uri = CameraCapture.createImageUri(context)
            // 相机是另一个进程，必须显式把写权限授给它，否则文件是空的
            CameraCapture.grantWritePermission(context, uri)
            uri
        }
            .onSuccess { uri ->
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
            .onFailure { cameraError = "无法创建照片文件：${it.message}" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            cameraError = "没有相机权限，拍不了照。可以到系统设置里开启，或者改用「选择图片」。"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "识别成绩单", onBack = onBack)

        when (state.stage) {
            ImportStage.Idle -> IdleContent(
                error = state.error ?: cameraError,
                onTakePhoto = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickFile = { filePicker.launch(DocumentExtractor.SUPPORTED_MIME_TYPES) },
                onDismissError = {
                    cameraError = null
                    viewModel.dismissError()
                }
            )

            ImportStage.Recognizing -> RecognizingContent()

            ImportStage.Preview -> state.preview?.let { exam ->
                PreviewContent(
                    exam = exam,
                    isImporting = state.isImporting,
                    error = state.error,
                    onExamNameChange = viewModel::updateExamName,
                    onScoreChange = viewModel::updateSubjectScore,
                    onFullScoreChange = viewModel::updateSubjectFullScore,
                    onRemoveSubject = viewModel::removeSubject,
                    onConfirm = viewModel::confirm,
                    onBackToIdle = viewModel::backToIdle,
                    onDismissError = viewModel::dismissError
                )
            }

            ImportStage.Done -> DoneContent(
                examId = state.importedExamId,
                onOpenExam = onOpenExam,
                onImportAnother = viewModel::backToIdle
            )
        }
    }
}

@Composable
private fun IdleContent(
    error: String?,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "导入成绩", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "支持拍照 / 图片、PDF、CSV、Excel。\n" +
                "AI 会自动提取考试名称、各科分数和错题。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        // 拍照放在第一位：对着成绩单按一下就走完，是最短的路径
        Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
            Text("拍一张成绩单")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text("选择图片 / 试卷照片")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Text("选择文件（PDF / CSV / Excel）")
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onDismissError) { Text("知道了") }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "图片 / PDF 走视觉模型，CSV / Excel 走文本模型 ——\n" +
                "记得先在「设置 → AI 配置」里填好模型信息。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecognizingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "正在识别…", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "视觉模型需要一点时间，请稍候",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewContent(
    exam: RecognizedExam,
    isImporting: Boolean,
    error: String?,
    onExamNameChange: (String) -> Unit,
    onScoreChange: (Int, String) -> Unit,
    onFullScoreChange: (Int, String) -> Unit,
    onRemoveSubject: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBackToIdle: () -> Unit,
    onDismissError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "识别完成，确认后导入",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "识别到 ${exam.subjects.size} 个科目 · " +
                    "${exam.wrongQuestionCount} 道错题 · " +
                    "总分 ${formatScore(exam.totalScore)}/${formatScore(exam.totalFullScore)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = exam.examName,
                onValueChange = onExamNameChange,
                label = { Text("考试名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        itemsIndexed(exam.subjects) { index, subject ->
            SubjectEditRow(
                index = index,
                subject = subject,
                onScoreChange = onScoreChange,
                onFullScoreChange = onFullScoreChange,
                onRemove = onRemoveSubject
            )
        }

        if (error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onDismissError) { Text("知道了") }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onConfirm,
                enabled = !isImporting && !exam.isEmpty,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isImporting) "正在导入…" else "确认导入")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBackToIdle,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("换一张图")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "导入后：各科分数进入「成绩」趋势，错题自动加入「错题本」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubjectEditRow(
    index: Int,
    subject: RecognizedSubject,
    onScoreChange: (Int, String) -> Unit,
    onFullScoreChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subject.subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (subject.questions.isNotEmpty()) {
                    Text(
                        text = "${subject.questions.size} 道错题",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { onRemove(index) }) { Text("移除") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formatScore(subject.score),
                    onValueChange = { onScoreChange(index, it) },
                    label = { Text("得分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = formatScore(subject.fullScore),
                    onValueChange = { onFullScoreChange(index, it) },
                    label = { Text("满分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DoneContent(
    examId: Long?,
    onOpenExam: (Long) -> Unit,
    onImportAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "导入完成 ✓", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "考试成绩和错题都已经写进去了。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { examId?.let(onOpenExam) },
            enabled = examId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("查看这次考试")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onImportAnother, modifier = Modifier.fillMaxWidth()) {
            Text("再导入一张")
        }
    }
}