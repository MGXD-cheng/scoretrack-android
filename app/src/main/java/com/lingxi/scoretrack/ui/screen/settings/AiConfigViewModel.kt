package com.lingxi.scoretrack.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState: StateFlow<AiConfigUiState> = _uiState.asStateFlow()

    init {
        // 读的是 EncryptedSharedPreferences，Key 在磁盘上始终是密文
        _uiState.value = AiConfigUiState(
            isLoading = false,
            text = AiModelForm.from(aiRepository.textConfig()),
            vision = AiModelForm.from(aiRepository.visionConfig())
        )
    }

    fun updateText(transform: (AiModelForm) -> AiModelForm) {
        _uiState.update {
            it.copy(text = transform(it.text), textTest = ConnectionState.Idle, savedHint = null)
        }
    }

    fun updateVision(transform: (AiModelForm) -> AiModelForm) {
        _uiState.update {
            it.copy(
                vision = transform(it.vision),
                visionTest = ConnectionState.Idle,
                savedHint = null
            )
        }
    }

    fun toggleTextKeyVisibility() {
        _uiState.update { it.copy(textKeyVisible = !it.textKeyVisible) }
    }

    fun toggleVisionKeyVisibility() {
        _uiState.update { it.copy(visionKeyVisible = !it.visionKeyVisible) }
    }

    fun save() {
        val state = _uiState.value
        aiRepository.saveTextConfig(state.text.toConfig())
        aiRepository.saveVisionConfig(state.vision.toConfig())
        _uiState.update { it.copy(savedHint = "已保存") }
    }

    /** 恢复文本模型的默认地址（DeepSeek） */
    fun resetTextToDefault() {
        _uiState.update {
            it.copy(text = AiModelForm.from(aiRepository.textConfig()).copy(
                baseUrl = "https://api.deepseek.com",
                modelName = "deepseek-chat"
            ))
        }
    }

    fun testTextConnection() {
        _uiState.update { it.copy(textTest = ConnectionState.Running) }
        viewModelScope.launch {
            aiRepository.testConnection(_uiState.value.text.toConfig())
                .onSuccess { reply ->
                    _uiState.update {
                        it.copy(
                            textTest = ConnectionState.Success(
                                "连接成功，模型回复：${reply.take(40)}"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            textTest = ConnectionState.Failure(
                                error.message ?: "连接失败，请检查地址与 Key"
                            )
                        )
                    }
                }
        }
    }

    fun testVisionConnection() {
        _uiState.update { it.copy(visionTest = ConnectionState.Running) }
        viewModelScope.launch {
            aiRepository.testConnection(_uiState.value.vision.toConfig())
                .onSuccess { reply ->
                    _uiState.update {
                        it.copy(
                            visionTest = ConnectionState.Success(
                                "连接成功，模型回复：${reply.take(40)}"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            visionTest = ConnectionState.Failure(
                                error.message ?: "连接失败，请检查地址与 Key"
                            )
                        )
                    }
                }
        }
    }
}