package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.model.KnowledgePointRow
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgePointDao {

    /** 知识点总览：带上每个知识点挂了多少道题 */
    @Query(
        """
        SELECT kp.id AS id,
               kp.name AS name,
               kp.subjectId AS subjectId,
               subject.name AS subjectName,
               kp.source AS source,
               COUNT(link.questionId) AS questionCount
        FROM knowledge_point kp
        LEFT JOIN subject ON subject.id = kp.subjectId
        LEFT JOIN question_knowledge link ON link.knowledgePointId = kp.id
        WHERE kp.profileId = :profileId
        GROUP BY kp.id
        ORDER BY questionCount DESC, kp.name ASC
        """
    )
    fun observeOverview(profileId: Long): Flow<List<KnowledgePointRow>>

    @Query("SELECT * FROM knowledge_point WHERE profileId = :profileId AND subjectId = :subjectId AND name = :name LIMIT 1")
    suspend fun findByName(profileId: Long, subjectId: Long, name: String): KnowledgePointEntity?

    @Query("SELECT * FROM knowledge_point WHERE profileId = :profileId ORDER BY name ASC")
    fun observeByProfile(profileId: Long): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_point WHERE profileId = :profileId AND subjectId = :subjectId ORDER BY name ASC")
    fun observeBySubject(profileId: Long, subjectId: Long): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_point WHERE profileId = :profileId AND subjectId = :subjectId ORDER BY name ASC")
    suspend fun getBySubject(profileId: Long, subjectId: Long): List<KnowledgePointEntity>

    @Query("SELECT * FROM knowledge_point WHERE profileId = :profileId")
    suspend fun getByProfile(profileId: Long): List<KnowledgePointEntity>

    @Query("SELECT * FROM knowledge_point WHERE id = :id")
    suspend fun getById(id: Long): KnowledgePointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: KnowledgePointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<KnowledgePointEntity>): List<Long>

    @Update
    suspend fun update(point: KnowledgePointEntity)

    @Update
    suspend fun updateAll(points: List<KnowledgePointEntity>)

    @Delete
    suspend fun delete(point: KnowledgePointEntity)

    @Query("DELETE FROM knowledge_point WHERE id = :id")
    suspend fun deleteById(id: Long)
}