package com.lingxi.scoretrack.ui.screen.sync

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.domain.sync.DiscoveredDevice
import com.lingxi.scoretrack.ui.components.AppTopBar

/**
 * 局域网同步。
 *
 * 页面结构刻意做成「两种模式」而不是「一个自动流程」——
 * 因为两台设备上要干的事不一样：一台开服务、另一台去连。
 * 用自动流程的话，用户得先猜自己该干嘛。
 */
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 手动输入地址的草稿。放在这里而不是 receiveContent 里 ——
    // 那个函数是 LazyListScope 的扩展，不是 @Composable 上下文，用不了 remember。
    var manualAddress by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "局域网同步", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ModeSwitcher(state.mode, viewModel::switchMode) }

            when (state.mode) {
                SyncMode.Send -> sendContent(state, viewModel)
                SyncMode.Receive -> receiveContent(
                    state = state,
                    viewModel = viewModel,
                    manualAddress = manualAddress,
                    onManualAddressChange = { manualAddress = it }
                )
            }

            if (state.error != null) {
                item {
                    MessageCard(
                        text = state.error.orEmpty(),
                        isError = true,
                        onDismiss = viewModel::dismissMessage
                    )
                }
            } else if (state.message != null) {
                item {
                    MessageCard(
                        text = state.message.orEmpty(),
                        isError = false,
                        onDismiss = viewModel::dismissMessage
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// 模式切换
// ---------------------------------------------------------------------------

@Composable
private fun ModeSwitcher(mode: SyncMode, onSwitch: (SyncMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == SyncMode.Send,
            onClick = { onSwitch(SyncMode.Send) },
            label = { Text("发送到另一台") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = mode == SyncMode.Receive,
            onClick = { onSwitch(SyncMode.Receive) },
            label = { Text("从另一台接收") },
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// 发送端
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.sendContent(
    state: SyncUiState,
    viewModel: SyncViewModel
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
                    text = "在另一台手机上打开这个页面，切到「从另一台接收」，" +
                        "它就能找到这台手机。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "这两台设备要连同一个 WiFi（用其中一台开热点也行）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "本机有 ${state.localRecordCount} 条记录待同步",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!state.isServing) {
                    Button(
                        onClick = viewModel::startServing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始等待接收")
                    }
                } else {
                    Text(
                        text = "把这 6 位数字告诉另一台手机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.pairingCode,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "如果自动扫描没找到，可以在另一台手动输入：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.serverUrl.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = viewModel::stopServing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("停止等待")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 接收端
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.receiveContent(
    state: SyncUiState,
    viewModel: SyncViewModel,
    manualAddress: String,
    onManualAddressChange: (String) -> Unit
) {
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "第 1 步 · 找到发送方",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = viewModel::scan,
                    enabled = !state.isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isScanning) "正在扫描…" else "扫描附近的设备")
                }

                if (state.devices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    state.devices.forEach { device ->
                        DeviceRow(
                            device = device,
                            selected = state.selectedDevice?.ip == device.ip,
                            onSelect = { viewModel.selectDevice(device) }
                        )
                    }
                }
            }
        }
    }

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "或者手动输入对方地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = onManualAddressChange,
                        placeholder = { Text("192.168.1.5:8080") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.probeManualAddress(manualAddress) }) {
                        Text("连接")
                    }
                }
            }
        }
    }

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "第 2 步 · 输入配对码",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "发送方屏幕上显示的 6 位数字",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.pairingInput,
                    onValueChange = viewModel::updatePairingInput,
                    placeholder = { Text("000000") },
                    singleLine = true,
                    // 数字键盘，省得用户从对面的屏幕抄数字时还切输入法
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.selectedDevice != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "将从「${state.selectedDevice.name}」接收数据",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = viewModel::receive,
                    enabled = state.canReceive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.phase == SyncPhase.Working) "正在同步…" else "开始同步")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已有的数据不会被删除，重复的内容会自动跳过。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = device.ip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageCard(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}