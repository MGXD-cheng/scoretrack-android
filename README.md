# 学迹 ScoreTrack

Android 端、纯本地存储的学生个人成绩与试卷分析工具。

> 当前进度：**v0.5.0 —— 去掉错题本，补上成绩反思，加了桌面小组件**

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + SQLite（11 张表） |
| 依赖注入 | Hilt |
| 网络 / AI | Retrofit + OkHttp + kotlinx.serialization |
| 密钥存储 | EncryptedSharedPreferences |
| 图表 | MPAndroidChart v3.1.0（折线趋势 / 科目雷达 / 失分饼图）+ Compose 自绘热力图 |
| 导航 | Navigation Compose |

## 已实现

- [x] 工程骨架：包名 `com.lingxi.scoretrack`，minSdk 27 / compileSdk 36
- [x] 品牌主题（蓝色系 + 深色模式 + 圆角规范）
- [x] Room 数据层：11 张表 + DAO + TypeConverter + schema 导出 + AutoMigration
- [x] Hilt DI：数据库与仓库全部注入
- [x] 多档案管理（新建档案自动预置 9 门科目：语数英 150，其余 100）
- [x] 手动录入成绩、排名、考试、科目
- [x] 趋势折线图 + 科目均衡雷达图（**可叠加各科目标线**）
- [x] 失分构成饼图 + 科目 × 考试表现热力图
- [x] 考试目标：**只填各科目标分，总分自动相加**
- [x] CSV / Excel / Word 本地解析 + AI 提取成绩
- [x] **多人成绩文件：识别出所有学生，导入前选一个**（`filterTarget` 留痕）
- [x] **拍照 / 相册识别成绩单**（多模态模型）
- [x] 小题分录入（**按题号对齐**：同题号更新、新题号追加）
- [x] **题型分类**：录入小题分时可选题型
- [x] **成绩反思**：写一段复盘，可让 AI 顺着你的思路给建议（人话与 AI 建议分开存）
- [x] **桌面小组件**：激励语 + 目标差距 + 下一步计划（Glance，2x2 起，可拉宽）
- [x] AI 分析报告（Markdown 全文直接渲染在首页）
- [x] API Key 配置（DeepSeek 文本 + 视觉）
- [x] **局域网同步**：UDP 广播发现设备 + 6 位配对码 + 按业务键合并导入
- [x] **数据导出 / 恢复**：JSON 备份，格式与局域网同步一致，可直接给另一台设备恢复
- [x] **科目自定义**：每个档案一套，可增删改（名称 / 满分 / 颜色 / 顺序）
- [x] **考试类型自定义**：默认 7 种（含周测），可自建，存在设置表里跟着备份走
- [x] **档案数据对比**：平均得分率 / 考试次数横向比较，最高者标「领先」
- [x] **检查更新**：从 GitHub Releases 比对版本，用户主动触发
- [x] 切换档案自动刷新（`ProfileSession` 作为唯一数据源）

## 页面结构

底部 4 个 Tab：

| Tab | 内容 |
|---|---|
| **首页** | 档案 · 最近考试 · 趋势折线 · 科目雷达（含目标线）· 失分饼图 · 表现热力图 · 考试目标 · AI 分析全文 |
| 成绩 | 考试列表 → 试卷 / 小题分 / **成绩反思** / 导入 |
| 设置 | 档案 / 科目设置 / 档案对比 · AI 配置 · 数据（导入 / 备份 / 恢复 / 同步）· 关于（检查更新） |

> 图表曾经单独占一个 Tab，现已合并进首页 —— 它本来就是首页该有的主体内容。

## 数据表

`profile` / `subject` / `exam` / `score` / `exam_goal` / `paper` / `question` /
`analysis_report` / `import_task` / `sync_device` / `app_setting`

> v0.5.0 起 v2 → v3 的迁移会 DROP 掉 `mistake` / `knowledge_point` /
> `question_knowledge` 三张表，其余数据不动。

## AI 配置

文本与视觉**分开配置**：

| 通道 | 用途 |
|---|---|
| 文本模型 | 成绩解析、分析报告、成绩反思、CSV / Excel / Word 文本提取 |
| 视觉模型 | 成绩单照片、PDF、图片识别 |

两者默认都指向 DeepSeek（模型名于 2026-11-21 实测核对）：

- 文本：`deepseek-v4-pro`（纯文本，推理最强）
- 视觉：`deepseek-flash`（目前唯一支持图片输入的）

> 旧的 `deepseek-chat` / `deepseek-v4-flash-vision-exp` 现在仍能调通，但那只是
> 服务端的兼容别名（请求它们实际会回 `"model":"deepseek-flash"`），随时可能真下线。
> 如果这一年里服务商又换了名字，`AiModelConfig` 里的两个常量就是唯一需要改的地方。

**地址与 key 相同**。如果只填了文本配置，视觉配置会自动派生 ——
不用为了识别一张成绩单再去注册第二家服务商。

## 构建

```bash
./gradlew :app:assembleDebug     # 日常开发
./gradlew :app:assembleRelease   # 发布（开启混淆 + 资源压缩）
```

环境：JDK 17、Android SDK 36。

### 关于 release 包

release **开启了 R8 混淆与资源压缩**，体积约为 debug 的八分之一。

> debug 包 16.3 MB → release 包 2.2 MB

混淆规则在 `app/proguard-rules.pro`。开启混淆后**第一次构建通常会失败** ——
R8 会把「引用了但找不到的类」全列出来（多数是编译期注解，运行时根本不存在），
按提示补 `-dontwarn` 即可。这类问题**只在 release 出现**，debug 完全看不到。

**发布前请读 [RELEASE.md](RELEASE.md)** —— 里面写了发版步骤，以及产物验证方法
（`BUILD SUCCESSFUL` 只说明编译过了，不代表包是好的，这个项目真踩过这个坑）。

## 说明

数据默认纯本地存储；除用户主动配置的 AI 接口外，不对外发送任何数据。

**数据可以带走**：设置 → 数据 →「导出备份」会生成一个 JSON，
格式和局域网同步完全相同，所以备份文件直接可以给另一台设备恢复。

更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## 许可

MIT License © 2026 MGXD-cheng — 详见 [LICENSE](LICENSE)。

可自由使用、修改、打包分发与商用，保留版权与许可声明即可。