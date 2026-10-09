package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.MistakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeDao {

    @Query("SELECT * FROM mistake WHERE profileId = :profileId ORDER BY lastReviewAt DESC, id DESC")
    fun observeByProfile(profileId: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistake WHERE profileId = :profileId ORDER BY lastReviewAt DESC, id DESC")
    suspend fun getByProfile(profileId: Long): List<MistakeEntity>

    /** 待复习错题数（未掌握的都算） */
    @Query("SELECT COUNT(*) FROM mistake WHERE profileId = :profileId AND reviewStatus != '已掌握'")
    fun observePendingCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM mistake WHERE id = :id")
    suspend fun getById(id: Long): MistakeEntity?

    @Query("SELECT * FROM mistake WHERE profileId = :profileId AND questionId = :questionId LIMIT 1")
    suspend fun getByProfileAndQuestion(profileId: Long, questionId: Long): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mistake: MistakeEntity): Long

    @Update
    suspend fun update(mistake: MistakeEntity)

    @Delete
    suspend fun delete(mistake: MistakeEntity)

    @Query("DELETE FROM mistake WHERE questionId = :questionId")
    suspend fun deleteByQuestion(questionId: Long)
}