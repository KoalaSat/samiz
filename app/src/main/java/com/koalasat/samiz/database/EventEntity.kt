package com.koalasat.samiz.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification",
    indices = [
        Index(
            value = ["eventId"],
            name = "notification_by_eventId",
            unique = true,
        ),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val eventId: String,
    val createdAt: Long,
)
