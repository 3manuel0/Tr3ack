package com.example.tr3ack.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.tr3ack.data.entity.BodyWeightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weight_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryForDate(date: String): BodyWeightEntity?

    @Query("SELECT * FROM body_weight_entries WHERE date <= :date ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecentEntryOnOrBefore(date: String): BodyWeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: BodyWeightEntity): Long

    @Update
    suspend fun update(entry: BodyWeightEntity)

    @Delete
    suspend fun delete(entry: BodyWeightEntity)

    @Query("DELETE FROM body_weight_entries WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
