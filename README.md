# 学迹 ScoreTrack

Android 端、纯本地存储的学生个人成绩与试卷分析工具。

> 当前进度：**v0.1.0 —— P0 功能全部完成，UI 已跑通**

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + SQLite（14 张表） |
| 依赖注入 | Hilt |
| 网络 / AI | Retrofit + OkHttp + kotlinx.serialization |
| 密钥存储 | EncryptedSharedPreferences |
| 图表 | 自绘 Canvas（折线趋势图 + 雷达图） |
| 导航 | Navigation Compose |

## 已实现

- [x] 工程骨架：包名 `com.lingxi.scoretrack`，minSdk 27 / compileSdk 36
- [x] 品牌主题（蓝色系 + 深色模式 + 圆角规范）
- [x] Room 数据层：14 张表 + DAO + TypeConverter + schema 导出
- [x] Hilt DI：数据库与仓库全部注入
- [x] 多档案管理（新建档案自动预置 9 门科目：语数英 150，其余 100）
- [x] 手动录入成绩、排名、考试、科目
- [x] 趋势折线图 + 科目均衡雷达图（自绘，无三方图表库）
- [x] 考试目标：**只填各科目标分，总分自动相加**
- [x] CSV / Excel 本地解析 + AI 提取成绩
- [x] **拍照 / 相册识别成绩单**（多模态模型）
- [x] 小题分录入（**按题号对齐**：同题号更新、新题号追加）
- [x] 错题本：得分低于满分自动入本，改对之后自动移出
- [x] AI 分析报告（Markdown 全文直接渲染在首页）
- [x] API Key 配置（DeepSeek 文本 + 视觉）

## 页面结构

底部 4 个 Tab：

| Tab | 内容 |
|---|---|
| **首页** | 档案 · 最近考试 · 趋势图 · 雷达图 · 考试目标 · AI 分析全文 · 待复习错题 |
| 成绩 | 考试列表 → 试卷 / 小题分 / 导入 |
| 错题 | 错题列表 · 复习状态 · 知识点 |
| 设置 | AI 配置 · 导入 · 关于 |

> 图表曾经单独占一个 Tab，现已合并进首页 —— 它本来就是首页该有的主体内容。

## 数据表

`profile` / `subject` / `exam` / `score` / `exam_goal` / `paper` / `question` /
`knowledge_point` / `question_knowledge` / `mistake` / `analysis_report` /
`import_task` / `sync_device` / `app_setting`

## AI 配置

文本与视觉**分开配置**：

| 通道 | 用途 |
|---|---|
| 文本模型 | 成绩解析、分析报告、CSV / Excel 文本提取 |
| 视觉模型 | 成绩单照片、PDF、图片识别 |

两者默认都指向 DeepSeek：

- 文本：`deepseek-chat`
- 视觉：`deepseek-v4-flash-vision-exp`

**地址与 key 相同**。如果只填了文本配置，视觉配置会自动派生 ——
不用为了识别一张成绩单再去注册第二家服务商。

## 构建

```bash
./gradlew :app:assembleDebug
```

环境：JDK 17、Android SDK 36。

## 说明

数据默认纯本地存储；除用户主动配置的 AI 接口外，不对外发送任何数据。

## 许可

个人学习项目。