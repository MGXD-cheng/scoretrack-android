package com.lingxi.scoretrack.domain.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在局域网里扫到的另一台设备。
 */
data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val baseUrl: String
)

/**
 * 通过 UDP 广播找同一 WiFi 下的 ScoreTrack。
 *
 * ## 为什么不做成「手动输入 IP」
 * 手动输 IP 看起来实现最省事，但对用户来说是整条链路里最容易放弃的一步：
 * IP 是 `192.168.1.7` 这种毫无意义的数字，用户得先退出去、找到系统设置、
 * 翻到 WiFi 详情、抄下来、再回来输进去。任何一步出错都会让人以为
 * 「这个功能是坏的」。
 *
 * 广播探测是**一次发送、多个应答**，天然支持三台以上设备互相发现。
 */
@Singleton
class SyncDiscovery @Inject constructor() {

    /**
     * 扫一轮局域网。
     *
     * @param timeoutMs 收集应答的总时长。默认 3 秒 —— 局域网往返通常在
     *                  几十毫秒，3 秒足够等到所有设备，又不至于让用户
     *                  对着转圈等太久。
     */
    suspend fun discover(timeoutMs: Long = 3_000L): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val found = linkedMapOf<String, DiscoveredDevice>()

            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    // 单次 receive 的超时，比总预算短 —— 好让循环有机会
                    // 检查 deadline 然后退出
                    socket.soTimeout = 800

                    val payload = "${SyncServer.DISCOVER_MAGIC}|discover".toByteArray()
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            InetAddress.getByName("255.255.255.255"),
                            SyncServer.DISCOVERY_PORT
                        )
                    )

                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        val buffer = ByteArray(512)
                        val packet = DatagramPacket(buffer, buffer.size)

                        // 超时会抛 SocketTimeoutException —— 那是正常的，
                        // 代表这一轮没人应答，继续等就行
                        val ok = runCatching { socket.receive(packet) }.isSuccess
                        if (!ok) continue

                        val text = String(packet.data, 0, packet.length).trim()
                        val parts = text.split("|")
                        if (parts.size < 2 || parts[0] != SyncServer.DISCOVER_MAGIC) continue

                        val ip = packet.address?.hostAddress ?: continue
                        // 用 ip 做 key 去重：同一台设备可能因为多网卡应答多次
                        found[ip] = DiscoveredDevice(
                            name = parts[1].ifBlank { ip },
                            ip = ip,
                            baseUrl = "http://$ip:${SyncServer.PORT}"
                        )
                    }
                }
            }

            found.values.toList()
        }
}

/**
 * 从另一台设备拉取数据快照。
 */
@Singleton
class SyncClient @Inject constructor() {

    private val json = Json {
        // 对方可能是更新的版本，多出来的字段直接忽略，不要因此失败
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // 读超时给得久一点：快照可能几 MB，而对方还要现查数据库
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 探一下对面在不在、叫什么。
     *
     * 用于「用户手输 IP 后先验证再同步」——直接同步失败的话，
     * 用户分不清是 IP 错了还是配对码错了。
     */
    suspend fun ping(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${normalize(baseUrl)}/ping")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("对方返回 ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                // 简单取 device 字段，不建 DTO —— 一行正则够了，
                // 而且对面改字段名也不会让这里崩掉
                Regex("\"device\"\\s*:\\s*\"([^\"]*)\"").find(body)
                    ?.groupValues?.getOrNull(1)
                    ?: "未知设备"
            }
        }
    }

    suspend fun fetch(baseUrl: String, pairingCode: String): Result<SyncSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${normalize(baseUrl)}/snapshot")
                    .header("x-pairing-code", pairingCode.trim())
                    .build()

                client.newCall(request).execute().use { response ->
                    // 401 单独给一句人话 —— 这是唯一一个用户能自己修的错误
                    if (response.code == 401) {
                        throw IOException("配对码不对，请核对对方屏幕上显示的 6 位数字")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("对方返回 ${response.code}，检查两台设备是否在同一个 WiFi 下")
                    }

                    val body = response.body?.string()
                        ?: throw IOException("对方没有返回数据")

                    // 解析放在 IO 线程里做：几 MB 的 JSON 解析在主线程上是
                    // 明显的卡顿，而这个方法本来就已经在 withContext(IO) 里了
                    json.decodeFromString(SyncSnapshot.serializer(), body)
                }
            }
        }

    /** 容忍用户粘贴时带上末尾斜杠或多于的路径 */
    private fun normalize(baseUrl: String): String = baseUrl.trim().trimEnd('/')
}