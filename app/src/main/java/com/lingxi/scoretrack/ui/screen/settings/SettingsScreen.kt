package com.lingxi.scoretrack.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.BuildConfig
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.ui.components.AppTopBar

@Composable
fun SettingsScreen(
    onOpenAiConfig: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSubjects: () -> Unit,
    onOpenCompare: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val currentId by viewModel.currentProfileId.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val autoKnowledge by viewModel.autoKnowledge.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    // 正在编辑的档案。null = 没在编辑；新建走 showCreateDialog
    var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }

    // 导出：让用户挑存哪儿。用 SAF 而不是直接写 /sdcard ——
    // Android 10+ 的分区存储下应用无权随意写公共目录，
    // 而且用户往往更想自己决定放哪（网盘同步目录、SD 卡……）。
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    // 恢复：MIME 用 */* 而不是 application/json。
    // 很多文件管理器/网盘提供方对 .json 的 MIME 上报不一致
    // （有报 text/plain 的、有报 octet-stream 的），限定死会让用户
    // 明明看得见文件却选不中 —— 那种体验比列表杂一点糟糕得多。
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "设置")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("AI") }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "AI 配置",
                        subtitle = "文本模型 / 视觉模型、API Key 与连通性测试",
                        onClick = onOpenAiConfig
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "自动归纳知识点",
                        subtitle = "导入错题后自动让 AI 归类（会消耗 token）",
                        trailing = {
                            Switch(
                                checked = autoKnowledge,
                                onCheckedChange = viewModel::setAutoKnowledge
                            )
                        }
                    )
                }
            }

            item { SectionTitle("档案") }
            item {
                SettingsCard {
                    if (profiles.isEmpty()) {
                        SettingsRow(title = "暂无档案", subtitle = "先新建一个吧", enabled = false)
                    } else {
                        profiles.forEachIndexed { index, profile ->
                            ProfileRow(
                                profile = profile,
                                selected = profile.id == currentId,
                                onClick = { viewModel.switchProfile(profile) },
                                onEdit = { editingProfile = profile },
                                onDelete = { viewModel.requestDelete(profile) }
                            )
                            if (index != profiles.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "新建档案",
                        subtitle = "会自动预置 9 门科目",
                        onClick = { showCreateDialog = true }
                    )
                }
            }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "科目设置",
                        subtitle = "增删科目、改满分和颜色、调整顺序（只改当前档案）",
                        onClick = onOpenSubjects
                    )
                }
            }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "档案对比",
                        subtitle = "把几个档案的成绩、错题量横向放在一起看",
                        onClick = onOpenCompare
                    )
                }
            }

            item { SectionTitle("数据") }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "导入成绩单",
                        subtitle = "拍照识别，自动录入分数与错题",
                        onClick = onOpenImport
                    )
                    SettingsRow(
                        title = "导出备份",
                        subtitle = "把全部数据存成一个文件，可放网盘",
                        enabled = !isBackupBusy,
                        onClick = {
                            createDocument.launch(viewModel.suggestedBackupName())
                        }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "从备份恢复",
                        subtitle = "选择之前导出的文件，合并回本机",
                        enabled = !isBackupBusy,
                        onClick = {
                            openDocument.launch(arrayOf("*/*"))
                        }
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "局域网同步",
                        subtitle = "把数据传到另一台手机（同一 WiFi）",
                        onClick = onOpenSync
                    )
                }
            }

            item { SectionTitle("关于") }
            item {
                SettingsCard {
                    SettingsRow(
                        title = "学迹 ScoreTrack",
                        subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                        enabled = false
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "检查更新",
                        subtitle = if (isCheckingUpdate) {
                            "正在检查…"
                        } else {
                            "从 GitHub 获取最新版本"
                        },
                        enabled = !isCheckingUpdate,
                        onClick = viewModel::checkUpdate
                    )
                    HorizontalDivider()
                    SettingsRow(title = "本地优先", subtitle = "所有数据仅保存在本机", enabled = false)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // 备份 / 恢复的结果。用 AlertDialog 而不是 Snackbar：
    // 这两件事都是用户主动触发、且需要知道具体结果的操作
    //（导出了多少条？是不是失败了？），一个会自己消失的提示不合适。
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = {
                Text(
                    when {
                        result.error != null -> "检查失败"
                        result.hasUpdate -> "有新版本 ${result.latestVersion}"
                        else -> "已是最新版"
                    }
                )
            },
            text = {
                Column {
                    when {
                        result.error != null -> Text(result.error)

                        result.hasUpdate -> {
                            Text("当前版本 ${result.currentVersion}")
                            if (result.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = result.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> Text("当前就是最新版本 ${result.currentVersion}。")
                    }
                }
            },
            confirmButton = {
                if (result.hasUpdate) {
                    TextButton(
                        onClick = {
                            openUrl(
                                context,
                                result.apkUrl.ifBlank { result.releaseUrl }
                            )
                            viewModel.dismissUpdate()
                        }
                    ) {
                        Text(if (result.apkUrl.isNotBlank()) "下载安装包" else "去发布页")
                    }
                } else {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("知道了") }
                }
            },
            dismissButton = if (result.hasUpdate) {
                {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("以后再说") }
                }
            } else {
                null
            }
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissBackupMessage() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBackupMessage() }) {
                    Text("知道了")
                }
            },
            text = { Text(message) }
        )
    }

    if (showCreateDialog) {
        ProfileEditDialog(
            title = "新建档案",
            initial = null,
            existing = profiles.map { it.name to it.id },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, grade, className, remark ->
                viewModel.createProfile(name, grade, className, remark)
                showCreateDialog = false
            }
        )
    }

    editingProfile?.let { target ->
        ProfileEditDialog(
            title = "编辑档案",
            initial = target,
            existing = profiles.map { it.name to it.id },
            onDismiss = { editingProfile = null },
            onConfirm = { name, grade, className, remark ->
                viewModel.updateProfile(target, name, grade, className, remark)
                editingProfile = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除档案") },
            text = {
                Text(
                    "「${target.name}」及其全部数据（科目、考试、成绩、错题、分析报告等）" +
                        "将被永久删除，且无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** 行尾控件（开关之类）。给了它就不再挂整行点击 —— 会和控件抢事件 */
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled && trailing == null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
            // 副标题：有年级/班级就先显示它们（这两个字段之前一直是死的，
            // 没有任何入口能填），再补「默认」标记
            val meta = listOfNotNull(profile.gradeName, profile.className)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val subtitle = buildString {
                if (meta.isNotBlank()) append(meta)
                if (profile.isDefault) {
                    if (isNotEmpty()) append(" · ")
                    append("默认档案")
                }
                if (isEmpty()) append("点击切换为当前档案")
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑档案",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除档案",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 用系统浏览器打开链接。
 *
 * 没有浏览器（或者链接被系统拒绝）时静默失败 —— 更新检查失败本来就不是
 * 致命问题，不该再弹一个崩溃对话框把用户吓一跳。
 */
private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}


/**
 * 新建 / 编辑档案共用的对话框。
 *
 * 四个字段分主次：名称必填，年级 / 班级 / 备注选填。这样用户想快速建个
 * 档案时只填一个名字就行，不会因为一堆空输入框停下来 —— 而需要区分的
 * 时候（比如帮两个人记）又能填全。
 */
@Composable
private fun ProfileEditDialog(
    title: String,
    initial: ProfileEntity?,
    /** 已有的（名称, id）。用来查重名，id 用来把自己排除掉 */
    existing: List<Pair<String, Long>>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, grade: String, className: String, remark: String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var grade by remember(initial?.id) { mutableStateOf(initial?.gradeName.orEmpty()) }
    var className by remember(initial?.id) { mutableStateOf(initial?.className.orEmpty()) }
    var remark by remember(initial?.id) { mutableStateOf(initial?.remark.orEmpty()) }

    val trimmed = name.trim()
    // 同名档案在侧栏、档案对比页里就是两个一模一样的东西，用户自己都分不清。
    // 与其建完再让他自己去删，不如在这里直接拦住。
    val duplicated = existing.any { it.first == trimmed && it.second != initial?.id }
    val nameError = when {
        trimmed.isEmpty() -> null
        duplicated -> "已经有同名档案了"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("档案名称") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("年级（选填）") },
                    placeholder = { Text("高二") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("班级（选填）") },
                    placeholder = { Text("3 班") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（选填）") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "年级和班级会显示在首页的档案卡片上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, grade, className, remark) },
                enabled = trimmed.isNotEmpty() && !duplicated
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
