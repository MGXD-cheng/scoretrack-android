package com.lingxi.scoretrack.domain.usecase

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.repository.AiRepository
import com.lingxi.scoretrack.data.repository.KnowledgePointRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.ai.KnowledgePointPrompt
import javax.inject.Inject
import javax.inject.Singleton

/** 一次归纳的结果，用来给用户一个交代 */
data class GeneratedKnowledgeSummary(
    val questionCount: Int,
    val newPointCount: Int,
    val reusedPointCount: Int,
    val linkCount: Int
)

/**
 * 从错题本归纳知识点，并把每道错题挂到对应的知识点上。
 *
 * 这是「标准答案 → 知识点」和「知识点自动生成」的合并实现：
 * 两件事本来就该在一次调用里完成，模型只有看到全局才能既归纳得准、又归得对。
 */
@Singleton
class GenerateKnowledgePointsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val mistakeRepository: MistakeRepository,
    private val knowledgePointRepository: KnowledgePointRepository,
    private val subjectRepository: SubjectRepository,
    private val aiRepository: AiRepository
) {

    suspend fun invoke(profileId: Long): Result<GeneratedKnowledgeSummary> = runCatching {
        val rows = mistakeRepository.getDetailByProfile(profileId)
        if (rows.isEmpty()) {
            throw IllegalStateException("错题本还是空的 —— 先录入几道错题，我才有素材可以归纳")
        }

        val subjects = subjectRepository.getByProfile(profileId)
        if (subjects.isEmpty()) {
            throw IllegalStateException("这个档案下还没有科目")
        }
        val subjectNames = subjects.associate { it.id to it.name }
        val subjectIdByName = subjects.associate { it.name.trim() to it.id }
        val fallbackSubjectId = subjects.first().id

        // 序号（从 1 起）→ 题目行。只让模型引用序号，不让它碰 questionId：
        // 长数字模型很容易抄错，而抄错的关联会静默写进库里，比报错更麻烦。
        val rowByIndex = rows.mapIndexed { index, row -> (index + 1) to row }.toMap()

        val raw = aiRepository.ask(
            prompt = KnowledgePointPrompt.buildUserContent(rows, subjectNames),
            systemPrompt = KnowledgePointPrompt.SYSTEM
        ).getOrElse { throw it }

        val parsed = KnowledgePointPrompt.parse(raw)
            ?: throw IllegalStateException("模型的返回没法解析成知识点，换一个模型再试试")

        // 知识点归属哪个科目：优先采信「引用它的那些题」的科目，
        // 那是我库里的准确数据，比模型返回的科目名可靠。
        val subjectOfPoint = mutableMapOf<String, Long>()
        parsed.assignments.forEach { (index, names) ->
            val row = rowByIndex[index] ?: return@forEach
            names.forEach { name -> subjectOfPoint.putIfAbsent(name, row.subjectId) }
        }

        val snapshot = database.withTransaction {
            val existing = knowledgePointRepository.getByProfile(profileId)
            // 名字匹配 + 别名匹配：同一个知识点换个说法，不该重复建
            val byName = existing.associateBy { it.name.trim() }.toMutableMap()
            val byAlias = existing
                .flatMap { point -> point.aliases.map { it.trim() to point } }
                .toMap()

            val idOfPoint = mutableMapOf<String, Long>()
            var newPoints = 0
            var reusedPoints = 0

            parsed.points.forEach { draft ->
                val matched = byName[draft.name] ?: byAlias[draft.name]
                val pointId = if (matched != null) {
                    reusedPoints++
                    // 把新发现的别名并进去，下次更容易命中同一个知识点
                    val mergedAliases = (matched.aliases + draft.aliases).distinct()
                    if (mergedAliases.size != matched.aliases.size) {
                        knowledgePointRepository.update(matched.copy(aliases = mergedAliases))
                    }
                    byName[draft.name] = matched
                    matched.id
                } else {
                    newPoints++
                    val subjectId = subjectOfPoint[draft.name]
                        ?: subjectIdByName[draft.subjectName]
                        ?: fallbackSubjectId
                    val entity = KnowledgePointEntity(
                        profileId = profileId,
                        subjectId = subjectId,
                        name = draft.name,
                        aliases = draft.aliases,
                        source = "ai"
                    )
                    val newId = knowledgePointRepository.insert(entity)
                    byName[draft.name] = entity.copy(id = newId)
                    newId
                }
                idOfPoint[draft.name] = pointId
            }

            // 题目 ↔ 知识点。DAO 用的是 IGNORE 策略，所以重复跑不会产生重复关联
            var links = 0
            parsed.assignments.forEach { (index, names) ->
                val row = rowByIndex[index] ?: return@forEach
                names.forEach { name ->
                    val pointId = idOfPoint[name] ?: return@forEach
                    knowledgePointRepository.link(row.questionId, pointId)
                    links++
                }
            }

            Triple(newPoints, reusedPoints, links)
        }

        GeneratedKnowledgeSummary(
            questionCount = rows.size,
            newPointCount = snapshot.first,
            reusedPointCount = snapshot.second,
            linkCount = snapshot.third
        )
    }
}