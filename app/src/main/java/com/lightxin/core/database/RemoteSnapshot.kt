package com.lightxin.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "remote_snapshot", primaryKeys = ["userCode", "snapshotKey"])
data class RemoteSnapshotEntity(
    val userCode: String,
    val snapshotKey: String,
    val json: String,
    val fetchedAt: Long,
)

@Dao
interface RemoteSnapshotDao {
    @Query("SELECT * FROM remote_snapshot WHERE userCode = :user AND snapshotKey = :key")
    suspend fun get(user: String, key: String): RemoteSnapshotEntity?

    @Upsert
    suspend fun put(entity: RemoteSnapshotEntity)
}
