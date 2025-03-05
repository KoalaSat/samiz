package com.koalasat.samiz.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApplicationDao {
    @Query("SELECT EXISTS (SELECT 1 FROM notification WHERE eventId = :eventId)")
    fun existsEvent(eventId: String): Int

    @Query("SELECT * FROM notification GROUP BY eventId")
    fun getEvents(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvent(eventEntity: EventEntity): Long?
}
