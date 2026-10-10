package com.lingxi.scoretrack.data.local.model

/**
 * 知识点总览投影：知识点本身 + 它挂了多少道题。
 *
 * 用 LEFT JOIN 而不是 INNER JOIN —— 刚建好还没归类的知识点也要显示出来，
 * 否则用户建了个知识点却"看不见"，会以为没保存成功。
 */
data class KnowledgePointRow(
    val id: Long,
    val name: String,
    val subjectId: Long,
    val subjectName: String,
    val source: String,
    val questionCount: Int
)