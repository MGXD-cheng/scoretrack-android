package com.lingxi.scoretrack.domain.usecase

import android.content.Context
import android.net.Uri
import com.lingxi.scoretrack.data.file.DocumentExtractor
import com.lingxi.scoretrack.data.remote.dto.ChatMessageDto
import com.lingxi.scoretrack.data.repository.AiRepository
import com.lingxi.scoretrack.domain.ai.PaperQuestionsPrompt
import com.lingxi.scoretrack.domain.ai.VisionModelSupport
import com.lingxi.scoretrack.domain.model.QuestionDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从上传的文件里提取试卷的小题明细。
 *
 * 复用了成绩单识别的整套基础设施：同一套 [DocumentExtractor] 分派、
 * 同一套 AI 配置、同一套失败提示。区别只在提示词和解析器 ——
 * 那边要「各科总分」，这边要「每一道小题」。
 */
@Singleton
class ExtractPaperQuestionsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository
) {

    suspend fun invoke(uri: Uri): Result<List<QuestionDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val source = DocumentExtractor.extract(context, uri)

            val raw = when (source) {
                // 表格/文本走文本模型：更快、更便宜，而且表格本来就是结构化的
                is DocumentExtractor.Extracted.Text -> aiRepository.chat(
                    messages = listOf(
                        ChatMessageDto.text(role = "system", text = PaperQuestionsPrompt.SYSTEM),
                        ChatMessageDto.text(
                            role = "user",
                            text = PaperQuestionsPrompt.USER + "\n\n" + source.content
                        )
                    ),
                    config = aiRepository.textConfig().copy(temperature = 0.1, maxTokens = MAX_TOKENS)
                ).getOrElse { throw it }

                // 图片 / PDF 渲染出的图走视觉模型
                is DocumentExtractor.Extracted.Images -> {
                    val vision = aiRepository.visionConfig()
                    val textModel = aiRepository.textConfig()

                    // 纯文本模型读不了图 —— 默认文本模型就是 deepseek-chat，
                    // 一旦在这里回退过去，只会得到一句看不懂的服务端报错
                    val config = listOf(vision, textModel)
                        .firstOrNull {
                            it.isConfigured && !VisionModelSupport.isTextOnly(it.modelName)
                        }
                        ?: throw IllegalStateException(
                            if (!vision.isConfigured && !textModel.isConfigured) {
                                "AI 尚未配置：请先到「设置 → AI 配置」填写模型信息"
                            } else {
                                VisionModelSupport.noVisionModelReason(
                                    vision.modelName,
                                    textModel.modelName
                                )
                            }
                        )
                    aiRepository.chat(
                        messages = listOf(
                            ChatMessageDto.text(role = "system", text = PaperQuestionsPrompt.SYSTEM),
                            ChatMessageDto.withImages(
                                role = "user",
                                text = PaperQuestionsPrompt.USER,
                                imageDataUrls = source.dataUrls
                            )
                        ),
                        config = config.copy(temperature = 0.1, maxTokens = MAX_TOKENS)
                    ).getOrElse { throw it }
                }
            }

            PaperQuestionsPrompt.parse(raw)
                ?: throw IllegalStateException("模型的返回没法解析成题目列表，换个模型再试试")
        }
    }

    private companion object {
        /** 一份卷子的题目不少，上限给足 */
        const val MAX_TOKENS = 8192
    }
}