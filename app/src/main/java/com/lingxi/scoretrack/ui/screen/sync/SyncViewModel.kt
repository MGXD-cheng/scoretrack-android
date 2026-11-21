package com.lingxi.scoretrack.ui.screen.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.domain.sync.DiscoveredDevice
import com.lingxi.scoretrack.domain.sync.SnapshotExporter
import com.lingxi.scoretrack.domain.sync.SnapshotImporter
import com.lingxi.scoretrack.domain.sync.SyncClient
import com.lingxi.scoretrack.domain.sync.SyncDiscovery
import com.lingxi.scoretrack.domain.sync.SyncServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SyncMode { Send, Receive }

enum class SyncPhase { Idle, Working, Done }

data class SyncUiState(
    val mode: SyncMode = SyncMode.Send,

    // ---------- 发送端 ----------
    /** 服务是否已启动 */
    val isServing: Boolean = false,
    /** 本机地址，形如 http://192.168.1.5:8080 */
    val serverUrl: String? = null,
    /** 配对码，显示在本机屏幕上让用户告诉对面 */
    val pairingCode: String = "",
    /** 本机待同步的记录总数，让用户对「要传多少东西」有个预期 */
    val localRecordCount: Int = 0,

    // ---------- 接收端 ----------
    val isScanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,
    val pairingInput: String = "",

    // ---------- 通用 ----------
    val phase: SyncPhase = SyncPhase.Idle,
    val message: String? = null,
    val error: String? = null
) {
    val canReceive: Boolean
        get() = selectedDevice != null && pairingInput.trim().length >= 6 && phase != SyncPhase.Working

    /** 配对码是 6 位数字，输入长度够了才放开按钮 */
    val pairingInputComplete: Boolean
        get() = pairingInput.trim().length == 6
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val server: SyncServer,
    private val client: SyncClient,
    private val discovery: SyncDiscovery,
    private val exporter: SnapshotExporter,
    private val importer: SnapshotImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        refreshLocalCount()
    }

    fun switchMode(mode: SyncMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode, error = null, message = null) }
    }

    // ---------- 发送端 ----------

    fun startServing() {
        val url = server.start()
        if (url == null) {
            _uiState.update {
                it.copy(error = "没能启动服务 —— 请确认已连上 WiFi（热点也行）后重试")
            }
            return
        }
        _uiState.update {
            it.copy(
                isServing = true,
                serverUrl = url,
                pairingCode = server.pairingCode,
                error = null
            )
        }
    }

    fun stopServing() {
        server.stop()
        _uiState.update {
            it.copy(isServing = false, serverUrl = null, pairingCode = "")
        }
    }

    private fun refreshLocalCount() {
        viewModelScope.launch {
            runCatching { exporter.exportAll().totalRecords }
                .onSuccess { count -> _uiState.update { it.copy(localRecordCount = count) } }
        }
    }

    // ---------- 接收端 ----------

    fun scan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, devices = emptyList(), error = null) }

        viewModelScope.launch {
            val found = runCatching { discovery.discover() }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    devices = found,
                    // 只扫到一台就顺手选中，省掉一次点击
                    selectedDevice = found.firstOrNull() ?: state.selectedDevice,
                    error = if (found.isEmpty()) {
                        "没扫到设备。确认两台手机连的是同一个 WiFi，" +
                            "并且对方已经点了「开始接收」"
                    } else {
                        null
                    }
                )
            }
        }
    }

    /** 手输地址后先探一下，确认对面真的是 ScoreTrack */
    fun probeManualAddress(input: String) {
        val url = normalizeUrl(input)
        if (url == null) {
            _uiState.update { it.copy(error = "地址格式不对，应该像 192.168.1.5:8080") }
            return
        }

        _uiState.update { it.copy(phase = SyncPhase.Working, error = null, message = null) }
        viewModelScope.launch {
            client.ping(url)
                .onSuccess { name ->
                    _uiState.update {
                        it.copy(
                            phase = SyncPhase.Idle,
                            selectedDevice = DiscoveredDevice(name = name, ip = url, baseUrl = url),
                            message = "已连接到「$name」"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            phase = SyncPhase.Idle,
                            error = e.message ?: "连不上这个地址"
                        )
                    }
                }
        }
    }

    fun selectDevice(device: DiscoveredDevice) {
        _uiState.update { it.copy(selectedDevice = device, error = null, message = null) }
    }

    fun updatePairingInput(value: String) {
        // 只留数字，且最多 6 位 —— 用户从对面屏幕抄数字时容易带上空格
        val cleaned = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(pairingInput = cleaned, error = null) }
    }

    fun receive() {
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        if (!state.canReceive) return

        _uiState.update { it.copy(phase = SyncPhase.Working, error = null, message = null) }

        viewModelScope.launch {
            client.fetch(device.baseUrl, state.pairingInput)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(phase = SyncPhase.Idle, error = e.message ?: "拉取数据失败")
                    }
                }
                .onSuccess { snapshot ->
                    val result = runCatching { importer.import(snapshot) }
                        .getOrElse { e ->
                            _uiState.update {
                                it.copy(
                                    phase = SyncPhase.Idle,
                                    error = e.message ?: "写入本机数据库时出错"
                                )
                            }
                            return@onSuccess
                        }

                    _uiState.update {
                        it.copy(
                            phase = SyncPhase.Done,
                            message = "同步完成：${result.summary}",
                            error = if (result.isSuccess) null else result.error
                        )
                    }
                }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null, error = null) }

    /**
     * ViewModel 销毁时**必须**停掉服务。
     *
     * 否则用户离开页面后端口还开着，同一个 WiFi 下谁都能访问 ——
     * 而用户完全不知道。这是这一整个功能里最需要防的「静默暴露」。
     */
    override fun onCleared() {
        server.stop()
        super.onCleared()
    }

    /** 容忍用户只输 IP（补上默认端口），或者带上 http:// 前缀 */
    private fun normalizeUrl(input: String): String? {
        val cleaned = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleaned.isEmpty()) return null
        val withPort = if (cleaned.contains(':')) cleaned else "$cleaned:${SyncServer.PORT}"
        // 粗校验：至少得像个 IP 或主机名
        if (!withPort.contains('.')) return null
        return "http://$withPort"
    }
}