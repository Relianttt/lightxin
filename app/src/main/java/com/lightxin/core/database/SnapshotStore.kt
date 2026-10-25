package com.lightxin.core.database

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

data class Snapshot<T>(val value: T, val fetchedAt: Long)

@Singleton
class SnapshotStore @Inject constructor(
    private val database: LightXinDatabase,
    private val session: AccountSession,
) {
    private val gson = Gson()

    suspend fun <T> read(account: AccountContext, key: String, type: Type): Snapshot<T>? = withContext(Dispatchers.IO) {
        session.requireCurrent(account.lease)
        val row = database.remoteSnapshotDao().get(account.userCode, key)
        session.requireCurrent(account.lease)
        if (row == null) return@withContext null
        val value: T = try {
            gson.fromJson<T>(row.json, type) ?: return@withContext null
        } catch (_: JsonParseException) {
            return@withContext null
        }
        Snapshot(value, row.fetchedAt)
    }

    suspend fun <T> write(account: AccountContext, key: String, value: T) = withContext(Dispatchers.IO) {
        session.requireCurrent(account.lease)
        val row = RemoteSnapshotEntity(account.userCode, key, gson.toJson(value), System.currentTimeMillis())
        database.withTransaction {
            session.requireCurrent(account.lease)
            database.remoteSnapshotDao().put(row)
            session.requireCurrent(account.lease)
        }
    }
}

object SnapshotKeys {
    const val LaborSummary = "labor.summary"
    const val LaborActivities = "labor.activities"
    const val CreditOverview = "credit.overview"
    const val CreditRecords = "credit.records"
}
