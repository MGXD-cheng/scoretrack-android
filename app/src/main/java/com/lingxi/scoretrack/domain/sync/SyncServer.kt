package com.lingxi.scoretrack.domain.sync

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 局域网同步的**服务端**：把本机数据通过 HTTP 吐给另一台设备。
 *
 * ## 为什么需要配对码
 * 服务一开，**同一个 WiFi 下的任何设备都能访问**。家里 WiFi 还好，
 * 学校 / 奶茶店 / 公司 WiFi 什么都可能连上来。数据是一整个档案的成绩和
 * 错题，泄露出去不是小事。
 *
 * 所以启动时生成一个 6 位码，显示在本机屏幕上，客户端必须带上它 ——
 * 相当于一个临时密码。它只在本次服务期间有效，停止服务就作废。
 *
 * ## 为什么用 HTTP 而不是直接传 .db 文件
 * 传 .db 看起来更简单，但两台设备的**数据库版本可能不一样**
 * （一边刚升级、一边还没升级），直接替换会导致 Room 打开时 schema
 * 校验失败 —— 而这时的报错对用户来说完全无法理解。
 * 走 JSON 则可以在导入层做协议版本检查，明确告诉用户「请先升级 App」。
 */
@Singleton
class SyncServer @Inject constructor(
    private val exporter: SnapshotExporter,
    private val deviceNameProvider: DeviceNameProvider
) {

    private var server: NanoHTTPD? = null

    private var discoverySocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null

    /** 本次服务期间的配对码。停止后失效，下次启动换新的 */
    var pairingCode: String = ""
        private set

    val isRunning: Boolean get() = server != null

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * 启动服务。
     *
     * @return 本机的局域网地址，形如 `http://192.168.1.5:8080`；
     *         拿不到地址（没连 WiFi）时返回 null，调用方据此提示用户。
     */
    fun start(): String? {
        if (server != null) return localUrl()

        pairingCode = generatePairingCode()
        val nano = object : NanoHTTPD(PORT) {
            override fun serve(session: IHTTPSession): Response = handle(session)
        }

        return runCatching {
            nano.start(SOCKET_READ_TIMEOUT, false)
            server = nano
            startDiscoveryResponder()
            localUrl()
        }.getOrElse {
            server = null
            null
        }
    }

    fun stop() {
        server?.stop()
        server = null
        stopDiscoveryResponder()
        pairingCode = ""
    }

    /**
     * 应答局域网的 UDP 探测。
     *
     * 客户端不知道对端 IP，所以整个子网广播一句「谁在？」，
     * 我们回一句「我在，我叫 XX」。这样用户就不用手动抄 IP 了 ——
     * 而手动抄 IP 恰恰是这类功能最容易劝退人的一步。
     */
    private fun startDiscoveryResponder() {
        if (discoveryThread != null) return

        discoveryThread = Thread {
            runCatching {
                val socket = DatagramSocket(DISCOVERY_PORT).apply { broadcast = true }
                discoverySocket = socket

                while (!socket.isClosed) {
                    val buffer = ByteArray(256)
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(packet) }
                        .onSuccess {
                            val text = String(packet.data, 0, packet.length).trim()
                            // 只应答协议内的探测，不对任意 UDP 包回话
                            if (text.startsWith(DISCOVER_MAGIC)) {
                                val reply = "$DISCOVER_MAGIC|${deviceNameProvider.get()}"
                                val bytes = reply.toByteArray()
                                socket.send(
                                    DatagramPacket(
                                        bytes,
                                        bytes.size,
                                        packet.address,
                                        packet.port
                                    )
                                )
                            }
                        }
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopDiscoveryResponder() {
        runCatching { discoverySocket?.close() }
        discoverySocket = null
        discoveryThread = null
    }

    // -----------------------------------------------------------------------

    private fun handle(session: IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri.orEmpty()

        // 没有配对码的请求直接挡掉 —— 在这之前不返回任何业务信息
        val provided = session.parameters["code"]?.firstOrNull()
            ?: session.headers["x-pairing-code"]
        if (uri != "/ping" && provided != pairingCode) {
            return jsonResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                """{"error":"配对码不对"}"""
            )
        }

        return when (uri) {
            // 探测用：客户端扫描局域网时用它确认「对面是不是 ScoreTrack」
            "/ping" -> jsonResponse(
                NanoHTTPD.Response.Status.OK,
                """{"app":"ScoreTrack","device":"${deviceNameProvider.get()}","protocol":${SyncSnapshot.PROTOCOL_VERSION}}"""
            )

            "/snapshot" -> {
                // serve 是同步阻塞的，但导出是 suspend。这里用 runBlocking 是
                // 可接受的：nanohttpd 每个请求本来就在自己的线程里，
                // 阻塞它不会卡住主线程。
                val snapshot = kotlinx.coroutines.runBlocking { exporter.exportAll() }
                jsonResponse(
                    NanoHTTPD.Response.Status.OK,
                    json.encodeToString(SyncSnapshot.serializer(), snapshot)
                )
            }

            else -> jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                """{"error":"未知接口"}"""
            )
        }
    }

    private fun jsonResponse(status: NanoHTTPD.Response.Status, body: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            body
        )

    /**
     * 6 位数字码。
     *
     * 用 `Random` 而不是加密安全的随机源 —— 它的作用是「防止同一个 WiFi
     * 下的无关设备误连」，不是对抗暴力破解。而且有效窗口只有用户
     * 主动开启同步的那几分钟。
     */
    private fun generatePairingCode(): String =
        (100_000..999_999).random().toString()

    /**
     * 找本机的局域网 IPv4 地址。
     *
     * 不能直接用 WiFi 的 IP 查询接口 —— 有些设备会返回 `0.0.0.0`
     * 或者干脆需要额外权限。遍历网卡更可靠，而且能看到热点模式
     * （手机开热点时走的是 ap0 之类的接口）。
     */
    private fun localUrl(): String? {
        val ip = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        return ip?.let { "http://$it:$PORT" }
    }

    companion object {
        const val PORT = 8080
        const val SOCKET_READ_TIMEOUT = 30_000

        /** UDP 设备发现端口。和服务端口分开，避免和别的 HTTP 服务抢 */
        const val DISCOVERY_PORT = 8081

        /** 探测报文前缀。带前缀是为了不误答同端口上的其它 UDP 流量 */
        const val DISCOVER_MAGIC = "SCORETRACK_DISCOVER"
    }
}