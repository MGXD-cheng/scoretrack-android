package com.lingxi.scoretrack.domain.ai

/**
 * 成绩反思的提示词与数据摘要。
 *
 * 和 [AnalysisPrompt] 的分工很清楚：
 * - [AnalysisPrompt] 看的是**历次成绩的长程走势**，给的是阶段性规划；
 * - 这里只看**一场考试**，并且用户已经写下了自己的判断（反思），
 *   模型要做的是「顺着他的思路往下想」，而不是从头再分析一遍。
 *
 * 这个差别很重要：如果无视用户写的内容，生成的东西会像一份通用报告，
 * 用户会觉得「我写那一堆它根本没看」。
 */
object ReflectionPrompt {

    val SYSTEM: String = """
        你是一位温和但务实的学习教练。学生会给你一场考试的各科成绩，
        以及他自己写的一段「成绩反思」。

        请写一段针对**这一次考试**的复盘建议，格式要求：
        - 纯文本，不要 Markdown 标题、不要列表符号、不要代码块
        - 控制在 150–250 字，2 到 3 段
        - 直接称呼「你」，语气像一个了解你的老师

        内容要求：
        - **先接住学生的自我判断**：他说的其实对，就肯定并补充；说得不准确，
          就温和地指出另一种可能。不要假装赞成，也不要否定得刺耳
        - 结合具体科目和分数说话，指出这次最该抓的一两件事
        - 给出**下一次考试前就能做完**的具体动作，不要空话
        - 如果他的反思很空（比如只写了「考差了」），就先帮他找到可能的原因，
          再给动作
    """.trimIndent()

    const val USER_PREFIX = "这是我这次考试的成绩和我的反思：\n\n"

    /** 单科成绩，够算得分率就好 */
    data class Entry(
        val subjectName: String,
        val score: Float,
        val fullScore: Float,
        /** 该科的目标分，没设过就是 null */
        val target: Float? = null
    ) {
        val rate: Float?
            get() = if (fullScore > 0f) score / fullScore else null
    }

    /**
     * 拼数据摘要。
     *
     * 统计能算的先算好（得分率、达标情况），只让模型做它擅长的「解读」——
     * 它算算术并不可靠，前面几版就吃过亏。
     */
    fun buildDigest(
        examName: String,
        examType: String,
        entries: List<Entry>,
        reflection: String
    ): String = buildString {
        appendLine("考试：$examName（$examType）")

        val total = entries.sumOf { it.score.toDouble() }.toFloat()
        val full = entries.sumOf { it.fullScore.toDouble() }.toFloat()
        if (full > 0f) {
            appendLine("总分：${trim(total)} / ${trim(full)}（得分率 ${percent(total / full)}）")
        }

        appendLine()
        appendLine("各科：")
        entries.forEach { entry ->
            val rate = entry.rate
            append("- ${entry.subjectName}：${trim(entry.score)} / ${trim(entry.fullScore)}")
            if (rate != null) append("（得分率 ${percent(rate)}）")
            entry.target?.let { target ->
                val diff = entry.score - target
                append(
                    if (diff >= 0f) "，达标（目标 ${trim(target)}，超出 ${trim(diff)}）"
                    else "，未达标（目标 ${trim(target)}，差 ${trim(-diff)}）"
                )
            }
            appendLine()
        }

        // 最弱的一科单独点一下。得分率最低才有意义 —— 80/100 和 120/150
        // 直接比分数是错的，这个坑前面踩过
        entries.filter { it.rate != null }
            .minByOrNull { it.rate!! }
            ?.let { weakest ->
                appendLine()
                appendLine("得分率最低：${weakest.subjectName}（${percent(weakest.rate!!)}）")
            }

        appendLine()
        appendLine("学生自己写的反思：")
        appendLine(reflection.trim().ifBlank { "（没有写）" })
    }

    /** 去掉没意义的小数尾巴：132.0 -> 132，89.5 -> 89.5 */
    private fun trim(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    private fun percent(rate: Float): String = "${(rate * 100).toInt()}%"
}