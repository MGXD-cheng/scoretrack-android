package com.lingxi.scoretrack.ui.screen.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.ui.components.AppTopBar

@Composable
fun AiConfigScreen(
    onBack: () -> Unit,
    viewModel: AiConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "AI 配置",
            onBack = onBack,
            actions = {
                TextButton(onClick = { viewModel.save() }) {
                    Text(if (state.savedHint != null) state.savedHint!! else "保存")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "文本模型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ModelFormCard(
                    form = state.text,
                    keyVisible = state.textKeyVisible,
                    onToggleKeyVisibility = viewModel::toggleTextKeyVisibility,
                    onFormChange = viewModel::updateText,
                    extraFooter = {
                        ConnectionHint(state.textTest)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.testTextConnection() },
                                enabled = state.isTextConfigured &&
                                    state.textTest !is ConnectionState.Running
                            ) {
                                Text("测试连接")
                            }
                            OutlinedButton(onClick = { viewModel.resetTextToDefault() }) {
                                Text("恢复默认")
                            }
                        }
                    }
                )
            }

            item {
                Text(
                    text = "视觉模型（可选，用于拍照识别成绩单）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                ModelFormCard(
                    form = state.vision,
                    keyVisible = state.visionKeyVisible,
                    onToggleKeyVisibility = viewModel::toggleVisionKeyVisibility,
                    onFormChange = viewModel::updateVision,
                    extraFooter = {
                        ConnectionHint(state.visionTest)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.testVisionConnection() },
                            enabled = state.vision.baseUrl.isNotBlank() &&
                                state.vision.apiKey.isNotBlank() &&
                                state.visionTest !is ConnectionState.Running
                        ) {
                            Text("测试连接")
                        }
                    }
                )
            }

            item {
                Text(
                    text = "API Key 使用 AES256 加密后保存在本机，不会以明文落盘。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConnectionHint(state: ConnectionState) {
    when (state) {
        ConnectionState.Idle -> Unit
        ConnectionState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp))
            Text(
                text = "  正在连接…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is ConnectionState.Success -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        is ConnectionState.Failure -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ModelFormCard(
    form: AiModelForm,
    keyVisible: Boolean,
    onToggleKeyVisibility: () -> Unit,
    onFormChange: ((AiModelForm) -> AiModelForm) -> Unit,
    extraFooter: @Composable () -> Unit
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
                value = form.baseUrl,
                onValueChange = { value -> onFormChange { it.copy(baseUrl = value) } },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.apiKey,
                onValueChange = { value -> onFormChange { it.copy(apiKey = value) } },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = onToggleKeyVisibility) {
                        Text(if (keyVisible) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.modelName,
                onValueChange = { value -> onFormChange { it.copy(modelName = value) } },
                label = { Text("模型名") },
                placeholder = { Text("deepseek-chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.temperature,
                    onValueChange = { value -> onFormChange { it.copy(temperature = value) } },
                    label = { Text("温度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.maxTokens,
                    onValueChange = { value -> onFormChange { it.copy(maxTokens = value) } },
                    label = { Text("最大 Token") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "启用该模型", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = form.enabled,
                    onCheckedChange = { value -> onFormChange { it.copy(enabled = value) } }
                )
            }

            extraFooter()
        }
    }
}