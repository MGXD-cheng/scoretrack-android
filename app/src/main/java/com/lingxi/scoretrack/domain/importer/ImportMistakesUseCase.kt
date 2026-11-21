package com.lingxi.scoretrack.domain.importer

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.PaperRepository
import com.lingxi.scoretrack.data.repository.QuestionRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    /** 文本里出现、但这个档案里没有的科目名 */
    val missingSubjects: List<String>,
    val byType: Map<String, Int>
)

/**
 * 把解析好的错题批量落库。
 *
 * ## 为什么要造一个「考试」出来
 *
 * 数据模型是 exam → paper → question → mistake，题目必须挂在试卷上，
 * 试卷必须挂在考试上。想「只加一道错题」也得先有个根。
 *
 * 所以这里会建一条 `错题导入 MM-dd HH:mm` 的考试记录，每门科目一份试卷。
 * 好处是这些题在错题本里能点进详情、能显示「来自哪次考试的哪一科」，
 * 和正常录入的错题完全一样 —— 不是二等公民。
 *
 * 代价是成绩页会多出这条导入记录。它没有分数（totalScore = 0），
 * 类型标成「其他」，不会混进平均分统计（统计只看得分率 > 0 的）。
 */
@Singleton
class ImportMistakesUseCase @Inject constructor(
    private val database: AppDatabase,
    private val examRepository: ExamRepository,
    private val paperRepository: PaperRepository,
    private val questionRepository: QuestionRepository,
    private val mistakeRepository: MistakeRepository,
    private val subjectRepository: SubjectRepository
) {

    suspend fun invoke(profileId: Long, rows: List<ParsedMistake>): ImportSummary =
        database.withTransaction {
            if (rows.isEmpty()) {
                return@withTransaction ImportSummary(0, 0, emptyList(), emptyMap())
            }

            val subjectsByName = subjectRepository.getByProfile(profileId)
                .associateBy { it.name.trim() }

            val grouped = rows.groupBy { it.subjectName.trim() }
            val matched = grouped.filterKeys { it in subjectsByName }
            val missing = grouped.keys.filterNot { it in subjectsByName }

            // 一个科目都对不上时直接返回，**不要**留下一条空考试
            if (matched.isEmpty()) {
                return@withTransaction ImportSummary(
                    imported = 0,
                    skipped = rows.size,
                    missingSubjects = missing,
                    byType = emptyMap()
                )
            }

            val now = System.currentTimeMillis()
            val examId = examRepository.insert(
                ExamEntity(
                    profileId = profileId,
                    name = "错题导入 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(now)),
                    type = "其他",
                    date = now,
                    remark = "从文本批量导入"
                )
            )

            var imported = 0
            val byType = mutableMapOf<String, Int>()

            matched.forEach { (subjectName, list) ->
                val subject = subjectsByName.getValue(subjectName)
                val paperId = paperRepository.insert(
                    PaperEntity(
                        examId = examId,
                        subjectId = subject.id,
                        title = "${subject.name}错题导入",
                        totalScore = 0f,
                        sourceType = "import"
                    )
                )

                list.forEachIndexed { index, row ->
                    val questionId = questionRepository.insert(
                        QuestionEntity(
                            paperId = paperId,
                            number = (index + 1).toString(),
                            type = row.type,
                            // 导入的错题没有分数信息 —— 留 0 而不是瞎猜
                            fullScore = 0f,
                            score = 0f,
                            content = row.content,
                            answer = row.answer.takeIf { it.isNotBlank() },
                            userAnswer = row.userAnswer.takeIf { it.isNotBlank() },
                            isCorrect = false,
                            wrongReason = row.wrongReason.takeIf { it.isNotBlank() }
                        )
                    )
                    mistakeRepository.add(
                        profileId = profileId,
                        questionId = questionId,
                        note = row.wrongReason.takeIf { it.isNotBlank() }
                    )
                    imported++
                    byType[row.type] = (byType[row.type] ?: 0) + 1
                }
            }

            ImportSummary(
                imported = imported,
                skipped = rows.size - imported,
                missingSubjects = missing,
                byType = byType
            )
        }
}