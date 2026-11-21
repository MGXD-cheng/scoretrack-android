package com.lingxi.scoretrack.domain.sync

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.entity.MistakeEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.QuestionKnowledgeEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.domain.exam.ExamTypeCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次导入的统计。
 *
 * 分「新建 / 更新 / 跳过」三档而不是只报一个总数 —— 用户看到
 * 「跳过 42 条」时至少知道同步没白跑，而不是以为数据全丢了。
 */
data class ImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null

    val summary: String
        get() = if (!isSuccess) {
            error.orEmpty()
        } else if (created == 0 && updated == 0) {
            "没有需要同步的新内容 —— 两台设备的数据已经一致"
        } else {
            "新增 $created 条，更新 $updated 条" +
                if (skipped > 0) "，跳过 $skipped 条（本机已有更新的版本）" else ""
        }
}

/**
 * 把一份 [SyncSnapshot] 合并进本机数据库。
 *
 * ## id 重映射（这是整个类最核心的事）
 * 快照里的 id 是**源端**的。同一台设备上 `exam.id = 3` 是「高二上期中」，
 * 到了本机 `id = 3` 可能是完全另一场考试。所以：
 * **绝对不能拿快照里的 id 直接插库**，必须先过映射表换成本机 id。
 * 每个 `xxxMap` 就是干这个的，子表靠它找到父表。
 *
 * ## 合并策略
 * 每张表都有自己的**业务键**，用它判重：
 *
 * | 表 | 业务键 |
 * |---|---|
 * | profile | 档案名 |
 * | subject | 档案 + 科目名 |
 * | exam | 档案 + 名称 + 日期 |
 * | score | 考试 + 科目 |
 * | paper | 考试 + 科目 |
 * | question | 试卷 + 题号 |
 * | knowledge_point | 档案 + 科目 + 名称 |
 * | mistake | 档案 + 题目 |
 *
 * 命中已有数据时，**按 `updatedAt` 新者胜**；没有 `updatedAt` 的表
 * （mistake / analysis_report）见各自的注释。
 *
 * ## 整个导入在一个事务里
 * 中途失败就全部回滚。宁可「什么都没同步」，也不要「一半数据进来了、
 * 另一半还是旧的」—— 那种状态用户在界面上根本看不出来。
 */
@Singleton
class SnapshotImporter @Inject constructor(
    private val db: AppDatabase
) {

    suspend fun import(snapshot: SyncSnapshot): ImportResult {
        // 协议版本先校验。比当前 App 新的快照直接拒绝 ——
        // 硬解析只会得到一堆「字段都对但值是空的」垃圾数据
        if (snapshot.protocolVersion > SyncSnapshot.PROTOCOL_VERSION) {
            return ImportResult(
                error = "这份数据来自更新版本的 App（协议 v${snapshot.protocolVersion}），" +
                    "请先升级本机 App 再同步"
            )
        }

        return db.withTransaction {
            var created = 0
            var updated = 0
            var skipped = 0

            val profileMap = mutableMapOf<Long, Long>()
            val subjectMap = mutableMapOf<Long, Long>()
            val examMap = mutableMapOf<Long, Long>()
            val paperMap = mutableMapOf<Long, Long>()
            val questionMap = mutableMapOf<Long, Long>()
            val kpMap = mutableMapOf<Long, Long>()

            // ---------- 1. 档案 ----------
            val localProfiles = db.profileDao().getAllOnce()
            snapshot.profiles.forEach { dto ->
                val existing = localProfiles.firstOrNull { it.name == dto.name }
                val id = if (existing != null) {
                    if (dto.updatedAt > existing.updatedAt) {
                        // 刻意不动 isDefault —— 哪份是默认档案是**本机**的事，
                        // 从另一端同步过来只会让用户莫名其妙
                        db.profileDao().update(
                            existing.copy(
                                className = dto.className,
                                gradeName = dto.gradeName,
                                remark = dto.remark,
                                updatedAt = dto.updatedAt
                            )
                        )
                        updated++
                    } else {
                        skipped++
                    }
                    existing.id
                } else {
                    created++
                    db.profileDao().insert(
                        ProfileEntity(
                            name = dto.name,
                            isDefault = false,
                            className = dto.className,
                            gradeName = dto.gradeName,
                            remark = dto.remark,
                            createdAt = dto.createdAt,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
                profileMap[dto.id] = id
            }

            // ---------- 2. 科目 ----------
            snapshot.subjects.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                val existing = db.subjectDao().getByName(pid, dto.name)
                val id = existing?.id ?: run {
                    created++
                    db.subjectDao().insert(
                        SubjectEntity(
                            profileId = pid,
                            name = dto.name,
                            fullScore = dto.fullScore,
                            color = dto.color,
                            sortOrder = dto.sortOrder
                        )
                    )
                }
                if (existing != null) skipped++
                subjectMap[dto.id] = id
            }

            // ---------- 3. 考试 ----------
            snapshot.exams.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                val existing = db.examDao().findByNameAndDate(pid, dto.name, dto.date)
                val id = if (existing != null) {
                    if (dto.updatedAt > existing.updatedAt) {
                        db.examDao().update(
                            existing.copy(
                                type = dto.type,
                                remark = dto.remark,
                                updatedAt = dto.updatedAt
                            )
                        )
                        updated++
                    } else {
                        skipped++
                    }
                    existing.id
                } else {
                    created++
                    db.examDao().insert(
                        ExamEntity(
                            profileId = pid,
                            name = dto.name,
                            type = dto.type,
                            date = dto.date,
                            remark = dto.remark,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
                examMap[dto.id] = id
            }

            // ---------- 4. 考试目标 ----------
            snapshot.examGoals.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                // subjectId 为 null 表示总分目标，映射时保持 null
                val sid = dto.subjectId?.let { subjectMap[it] }
                val existing = db.examGoalDao().find(pid, sid)
                if (existing == null) {
                    created++
                    db.examGoalDao().upsert(
                        ExamGoalEntity(
                            profileId = pid,
                            subjectId = sid,
                            targetScore = dto.targetScore,
                            targetRank = dto.targetRank,
                            updatedAt = dto.updatedAt
                        )
                    )
                } else if (dto.updatedAt > existing.updatedAt) {
                    updated++
                    db.examGoalDao().upsert(
                        existing.copy(
                            targetScore = dto.targetScore,
                            targetRank = dto.targetRank,
                            updatedAt = dto.updatedAt
                        )
                    )
                } else {
                    skipped++
                }
            }

            // ---------- 5. 成绩 ----------
            snapshot.scores.forEach { dto ->
                val eid = examMap[dto.examId] ?: run { skipped++; return@forEach }
                val sid = subjectMap[dto.subjectId] ?: run { skipped++; return@forEach }
                val existing = db.scoreDao().getByExamAndSubject(eid, sid)
                if (existing == null) {
                    created++
                    db.scoreDao().insert(
                        ScoreEntity(
                            examId = eid,
                            subjectId = sid,
                            score = dto.score,
                            fullScore = dto.fullScore,
                            classAvg = dto.classAvg,
                            gradeAvg = dto.gradeAvg,
                            classRank = dto.classRank,
                            gradeRank = dto.gradeRank,
                            classTotal = dto.classTotal,
                            gradeTotal = dto.gradeTotal,
                            updatedAt = dto.updatedAt
                        )
                    )
                } else if (dto.updatedAt > existing.updatedAt) {
                    updated++
                    db.scoreDao().update(
                        existing.copy(
                            score = dto.score,
                            fullScore = dto.fullScore,
                            classAvg = dto.classAvg,
                            gradeAvg = dto.gradeAvg,
                            classRank = dto.classRank,
                            gradeRank = dto.gradeRank,
                            classTotal = dto.classTotal,
                            gradeTotal = dto.gradeTotal,
                            updatedAt = dto.updatedAt
                        )
                    )
                } else {
                    skipped++
                }
            }

            // ---------- 6. 试卷 ----------
            snapshot.papers.forEach { dto ->
                val eid = examMap[dto.examId] ?: run { skipped++; return@forEach }
                val sid = subjectMap[dto.subjectId] ?: run { skipped++; return@forEach }
                val existing = db.paperDao().getByExamAndSubject(eid, sid)
                val id = if (existing != null) {
                    if (dto.updatedAt > existing.updatedAt) {
                        updated++
                        db.paperDao().update(
                            existing.copy(
                                title = dto.title,
                                totalScore = dto.totalScore,
                                updatedAt = dto.updatedAt
                            )
                        )
                    } else {
                        skipped++
                    }
                    existing.id
                } else {
                    created++
                    db.paperDao().insert(
                        PaperEntity(
                            examId = eid,
                            subjectId = sid,
                            title = dto.title,
                            totalScore = dto.totalScore,
                            sourceType = dto.sourceType,
                            // 源文件路径是本机的，传过来就是坏链接 —— 刻意留空
                            sourceFileUrl = null,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
                paperMap[dto.id] = id
            }

            // ---------- 7. 题目 ----------
            snapshot.questions.forEach { dto ->
                val pid = paperMap[dto.paperId] ?: run { skipped++; return@forEach }
                val existing = db.questionDao().findByPaperAndNumber(pid, dto.number)
                val id = if (existing != null) {
                    if (dto.updatedAt > existing.updatedAt) {
                        updated++
                        db.questionDao().update(
                            existing.copy(
                                type = dto.type,
                                fullScore = dto.fullScore,
                                score = dto.score,
                                difficulty = dto.difficulty,
                                content = dto.content,
                                answer = dto.answer,
                                userAnswer = dto.userAnswer,
                                isCorrect = dto.isCorrect,
                                wrongReason = dto.wrongReason,
                                updatedAt = dto.updatedAt
                            )
                        )
                    } else {
                        skipped++
                    }
                    existing.id
                } else {
                    created++
                    db.questionDao().insert(
                        QuestionEntity(
                            paperId = pid,
                            number = dto.number,
                            type = dto.type,
                            fullScore = dto.fullScore,
                            score = dto.score,
                            difficulty = dto.difficulty,
                            content = dto.content,
                            answer = dto.answer,
                            userAnswer = dto.userAnswer,
                            isCorrect = dto.isCorrect,
                            wrongReason = dto.wrongReason,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
                questionMap[dto.id] = id
            }

            // ---------- 8. 知识点 ----------
            // 分两遍：parentId 指向的知识点可能在列表里排在后面，
            // 第一遍只建点，第二遍才补父子关系
            snapshot.knowledgePoints.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                val sid = subjectMap[dto.subjectId] ?: run { skipped++; return@forEach }
                val existing = db.knowledgePointDao().findByName(pid, sid, dto.name)
                val id = existing?.id ?: run {
                    created++
                    db.knowledgePointDao().insert(
                        KnowledgePointEntity(
                            profileId = pid,
                            subjectId = sid,
                            name = dto.name,
                            aliases = dto.aliases,
                            parentId = null,
                            source = dto.source,
                            updatedAt = dto.updatedAt
                        )
                    )
                }
                if (existing != null) skipped++
                kpMap[dto.id] = id
            }

            snapshot.knowledgePoints.forEach { dto ->
                val selfId = kpMap[dto.id] ?: return@forEach
                val parentId = dto.parentId?.let { kpMap[it] } ?: return@forEach
                val self = db.knowledgePointDao().getById(selfId) ?: return@forEach
                if (self.parentId != parentId) {
                    db.knowledgePointDao().update(self.copy(parentId = parentId))
                }
            }

            // ---------- 9. 题目 ↔ 知识点 ----------
            snapshot.questionKnowledge.forEach { dto ->
                val qid = questionMap[dto.questionId] ?: return@forEach
                val kid = kpMap[dto.knowledgePointId] ?: return@forEach
                // IGNORE 冲突：这条关联已经存在就当没发生
                db.questionKnowledgeDao().insert(QuestionKnowledgeEntity(qid, kid))
            }

            // ---------- 10. 错题 ----------
            snapshot.mistakes.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                val qid = questionMap[dto.questionId] ?: run { skipped++; return@forEach }
                val existing = db.mistakeDao().getByProfileAndQuestion(pid, qid)
                if (existing == null) {
                    created++
                    db.mistakeDao().insert(
                        MistakeEntity(
                            profileId = pid,
                            questionId = qid,
                            note = dto.note,
                            reviewStatus = dto.reviewStatus,
                            reviewCount = dto.reviewCount,
                            lastReviewAt = dto.lastReviewAt
                        )
                    )
                } else if (dto.reviewCount > existing.reviewCount) {
                    // 错题**没有 updatedAt**，而复习状态是用户在本机上
                    // 一次次刷出来的。所以只在「对面复习得更勤」时才覆盖 ——
                    // 反过来把本机的进度擦掉，用户会觉得「我的复习记录没了」
                    updated++
                    db.mistakeDao().update(
                        existing.copy(
                            note = dto.note ?: existing.note,
                            reviewStatus = dto.reviewStatus,
                            reviewCount = dto.reviewCount,
                            lastReviewAt = dto.lastReviewAt
                        )
                    )
                } else {
                    skipped++
                }
            }

            // ---------- 11. 分析报告 ----------
            snapshot.analysisReports.forEach { dto ->
                val pid = profileMap[dto.profileId] ?: run { skipped++; return@forEach }
                val existing = db.analysisReportDao()
                    .findByProfileAndCreatedAt(pid, dto.createdAt)
                if (existing != null) {
                    skipped++
                    return@forEach
                }
                created++
                db.analysisReportDao().insert(
                    AnalysisReportEntity(
                        profileId = pid,
                        // 报告关联的考试可能已被跳过，映射时会自然变成 null
                        examId = dto.examId?.let { examMap[it] },
                        type = dto.type,
                        content = dto.content,
                        createdAt = dto.createdAt
                    )
                )
            }

            // ---------- 12. 用户偏好设置 ----------
            // 分两类处理，因为这两种数据丢的方式不一样：
            //  - 累积型（自定义考试类型）：直接覆盖会把本机独有的类型抹掉，
            //    用户会莫名其妙少几个类型 —— 取并集。
            //  - 开关型（自动归纳等）：没有「累积」语义，以传入的快照为准。
            snapshot.settings.forEach { dto ->
                if (dto.key == ExamTypeCatalog.KEY_CUSTOM_TYPES) {
                    val localRaw = db.appSettingDao().get(dto.key)
                    val before = SettingMerger.decodeList(localRaw).size
                    val merged = SettingMerger.mergeEncoded(localRaw, dto.value)
                    // 只有真的多出来东西才写。否则每次同步都会把同一份
                    // 内容原样重写一遍，白改一次 updatedAt
                    if (SettingMerger.decodeList(merged).size != before) {
                        db.appSettingDao().put(AppSettingEntity(dto.key, merged))
                    }
                } else {
                    db.appSettingDao().put(AppSettingEntity(dto.key, dto.value))
                }
            }

            ImportResult(created = created, updated = updated, skipped = skipped)
        }
    }
}