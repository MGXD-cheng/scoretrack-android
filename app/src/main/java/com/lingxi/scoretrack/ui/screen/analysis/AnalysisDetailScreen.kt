package com.lingxi.scoretrack.ui.screen.analysis

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.repository.AnalysisReportRepository
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.components.MarkdownText
import com.lingxi.scoretrack.ui.util.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisDetailViewModel @Inject constructor(
    private val analysisReportRepository: AnalysisReportRepository
) : ViewModel() {

    private val reportId = MutableStateFlow(0L)

    val report: StateFlow<AnalysisReportEntity?> = reportId
        .flatMapLatest { id ->
            if (id <= 0L) flowOf(null) else analysisReportRepository.observeById(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun init(id: Long) {
        reportId.value = id
    }

    fun delete(onDeleted: () -> Unit) {
        val current = report.value ?: return
        viewModelScope.launch {
            analysisReportRepository.delete(current)
            onDeleted()
        }
    }
}

@Composable
fun AnalysisDetailScreen(
    reportId: Long,
    onBack: () -> Unit,
    viewModel: AnalysisDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(reportId) { viewModel.init(reportId) }
    val report by viewModel.report.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "分析报告",
            onBack = onBack,
            actions = {
                if (report != null) {
                    IconButton(
                        onClick = {
                            val text = report?.content ?: return@IconButton
                            shareText(context, text)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "分享报告"
                        )
                    }
                }
            }
        )

        val current = report
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        text = formatDateTime(current.createdAt) + " · " + current.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    MarkdownText(
                        markdown = current.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

/** 分享纯文本 Markdown。用 ACTION_SEND 而不是落文件，免去 FileProvider 配置 */
private fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "学习诊断报告")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享诊断报告"))
}