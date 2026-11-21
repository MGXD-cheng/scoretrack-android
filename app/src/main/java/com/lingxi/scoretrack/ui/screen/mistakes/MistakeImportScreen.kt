package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.domain.importer.MistakeTextParser
import com.lingxi.scoretrack.ui.components.AppTopBar

/**
 * 批量导入错题。
 *
 * 存在的理由：错题本是「积累」型数据，一道一道手录会让人放弃使用。
 * 但从 Excel / 纸质错题本 / 之前的笔记里搬过来，用户手里本来就是一段文本。
 */
@Composable
fun MistakeImportScreen(
    onBack: () -> Unit,
    viewModel: MistakeImportViewModel = hiltViewModel()
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "批量导入错题",
            onBack = onBack,
            actions = {
                TextButton(onClick = viewModel::fillSample) { Text("插入示例") }
                TextButton(onClick = viewModel::clear) { Text("清空") }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "一行一道题，用半角逗号分隔：",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = MistakeTextParser.COLUMNS,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "题型写「选择题 / 填空题 / 解答题」，写「选择」「单选」也能认出来。" +
                                "以 # 开头的行会被忽略，可以当注释用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (subjects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "当前档案的科目：" + subjects.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "这个档案还没有科目，先去「设置 → 科目设置」加几门。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = viewModel::updateText,
                    label = { Text("粘贴错题文本") },
                    placeholder = { Text("数学,选择题,题干,A,B,错因") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "可以导入 ${outcome.rows.size} 道题",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (outcome.countByType.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = outcome.countByType.entries.joinToString("　") {
                                    "${it.key} ${it.value} 道"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (outcome.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "${outcome.errors.size} 行有问题（这些行会被跳过）：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            outcome.errors.take(5).forEach { error ->
                                Text(
                                    text = "· $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (outcome.errors.size > 5) {
                                Text(
                                    text = "· 还有 ${outcome.errors.size - 5} 条…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.import(onBack) },
                enabled = outcome.rows.isNotEmpty() && !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("导入中")
                } else {
                    Text("导入 ${outcome.rows.size} 道错题")
                }
            }
        }
    }

    result?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = { Text(if (summary.imported > 0) "导入完成" else "没有导入任何内容") },
            text = {
                Column {
                    if (summary.imported > 0) {
                        Text("成功导入 ${summary.imported} 道错题。")
                        if (summary.byType.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = summary.byType.entries.joinToString("、") {
                                    "${it.key} ${it.value} 道"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (summary.missingSubjects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "这些科目在当前档案里不存在，对应内容已跳过：" +
                                summary.missingSubjects.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (summary.imported == 0 && summary.missingSubjects.isEmpty()) {
                        Text("请检查文本格式是否正确。")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResult) { Text("知道了") }
            }
        )
    }
}