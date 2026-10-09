# 学迹 ScoreTrack

Android 端、纯本地存储的学生个人成绩与试卷分析工具。

> 当前进度：**P0 第一阶段**（工程骨架 + Room 数据层 + Hilt DI），UI 尚未接入。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + SQLite（13 张表） |
| 依赖注入 | Hilt |
| 网络 / AI | Retrofit + OkHttp + kotlinx.serialization |
| 密钥存储 | EncryptedSharedPreferences |
| 图表 | MPAndroidChart |
| 导航 | Navigation Compose |
| 图片 | Coil |

## 已实现

- [x] 工程骨架：包名 `com.lingxi.scoretrack`，minSdk 27 / compileSdk 36
- [x] 品牌主题（蓝色系 + 深色模式 + 圆角规范）
- [x] Room 数据层：13 张表 + 13 个 DAO + TypeConverter + schema 导出
- [x] Hilt DI：数据库与仓库全部注入
- [x] 档案仓库：新建档案自动预置 9 门科目（语数英 150，其余 100）
- [x] 单元测试：6 个用例全部通过

## 数据表

`profile` / `subject` / `exam` / `score` / `paper` / `question` /
`knowledge_point` / `question_knowledge` / `mistake` / `analysis_report` /
`import_task` / `sync_device` / `app_setting`

## 构建

```bash
./gradlew :app:assembleDebug
```

环境：JDK 17、Android SDK 36。

## 路线图（P0）

- [x] Room 数据库 + 全部表结构
- [x] 多档案管理（数据层）
- [ ] 手动录入成绩、排名、考试、科目
- [ ] MPAndroidChart 折线图 + 雷达图
- [ ] CSV / Excel 本地解析 + AI 提取成绩
- [ ] 手动输入小题分
- [ ] 标准答案导入 → AI 推断知识点
- [ ] 知识点自动生成 + 手动触发归类
- [ ] AI 学习建议 + Markdown 报告导出
- [ ] API Key 配置（重点 DeepSeek）

## 说明

数据默认纯本地存储；除用户主动配置的 AI 接口与局域网同步外，不对外发送数据。

## 许可

个人学习项目。
