# 发布流程

面向「**以后的自己**」写的 —— 隔三个月再回来看，这上面的每一步都会显得陌生。

---

## ⚠️ 先读这段：密钥

应用是用 **`keystore/release.keystore`** 签名的，口令在同目录上一层的
`keystore.properties` 里。

```
keystore/release.keystore      ← 私钥本体（.gitignore 已排除）
keystore.properties            ← 口令（.gitignore 已排除）
```

**这两个文件必须备份到至少两个地方**（网盘 + 邮箱 / U 盘 / 打印出来）。

原因：

- Android 不允许用**不同签名**覆盖安装。换一次签名 = 所有用户必须卸载重装，
  数据全丢。
- Google Play 要求所有版本的签名一致。密钥丢了，这个应用**永远无法再更新**，
  只能当作一个新应用重新上架（老用户全部失联）。
- 没有找回机制。真的没有。

当前证书信息：

```
CN=Lingxi Tech, OU=ScoreTrack, O=Lingxi Tech, L=Hangzhou, ST=Zhejiang, C=CN
有效期 10950 天（约 30 年）
SHA-256: 51539c037555df02e9222e4940675230edf6ecf5ac75bcf7b7ea484e480cdea0
```

> 如果 keystore.properties 不存在（别人 clone 代码、CI 环境），构建会自动
> 退回 debug 签名，并在日志里打印警告。**那样的包不能发布。**

---

## 发版步骤

### 1. 改版本号

`app/build.gradle.kts`：

```kotlinversionCode = 3          // 每次发版 +1，绝对不能重复也不能减小
versionName = "0.3.0"    // 给用户看的```

**两处都要改。** 之前踩过的坑：只改了 versionName，
结果 APK 文件名和界面里显示的版本号对不上。

### 2. 更新 CHANGELOG.md

按 `新增 / 修复 / 改进 / 移除` 四段写。**写给用户看，不是写给 git log 看** ——
「优化了性能」这种话没有信息量，「首页冷启动从 1.2s 降到 0.8s」才有。

### 3. 跑完整验证

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

**然后必须验证产物** —— 光看 `BUILD SUCCESSFUL` 是不够的：

```bash
APKSIGNER=/root/Android/build-tools/35.0.0/apksigner
AAPT2=/root/Android/build-tools/35.0.0/aapt2

$APKSIGNER verify --print-certs app/build/outputs/apk/release/app-release.apk
$AAPT2 dump badging app/build/outputs/apk/release/app-release.apk | head -3
```

期望看到：

```
Signer #1 certificate DN: CN=Lingxi Tech, ...   ← 不是 Android Debug
package: name='com.lingxi.scoretrack' versionCode='3' versionName='0.3.0'
application-label:'学迹'
```

如果 `aapt2` 报 `could not identify format of APK`，说明产物里缺
`AndroidManifest.xml` —— 大概率是资源优化又抽风了，见
`gradle.properties` 里 `android.enableResourceOptimizations=false` 那段注释。

### 4. 归档产物

```bash
cp app/build/outputs/apk/release/app-release.apk \
   "/sdcard/Download/ScoreTrack-v0.3.0-release.apk"
```

---

## 已知的构建环境限制

这个项目在 **proot / Termux 环境**里构建，不是标准 Android Studio。踩到的坑：

| 现象 | 原因 | 处理 |
|---|---|---|
| AAPT2 daemon 启动失败 | proot 不支持某些系统调用 | `android.aapt2.process.daemon=false` |
| release 包缺 manifest/arsc，但构建不报错 | `optimizeReleaseResources` 在 proot 里不产出 | `android.enableResourceOptimizations=false` |
| 多行 shell 命令被吞 | 终端实现限制 | 用 `;` 把命令串成一行 |

**在正常的 Android Studio 环境里这三条都不需要**，但因为改了
`gradle.properties`，标准环境也照样能构建（只是少了那点优化）。

---

## 上架前还差什么

功能上是齐的，但这些**没有就上不了 Google Play**：

- [ ] **隐私政策**：即使用户数据只存在本地，Play 也要求有一个可访问的隐私政策 URL
- [ ] **数据安全表单**（Data Safety）：Play Console 里逐项声明收集/共享了什么
      —— 我们是「全部本地 + 可选 AI 接口」，AI 那部分必须如实说明会上传图片/文本
- [ ] **商店素材**：应用图标 512×512、feature graphic 1024×500、至少 2 张截图
- [ ] **AAB 而不是 APK**：Play 现在只收 `.aab`，需要加一个 `bundleRelease` 任务
- [ ] **AI 功能的说明**：用户必须自带 API Key 这件事得在商店描述里写清楚，
      否则会被当成「功能残缺」打差评

> 如果只是自己用、或者发到酷安/GitHub Releases，那上面这些除了
> 「AI 功能说明」之外都可以跳过，直接分发 APK 即可。
