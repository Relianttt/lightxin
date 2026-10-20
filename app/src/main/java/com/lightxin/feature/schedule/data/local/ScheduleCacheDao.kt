package com.lightxin.feature.schedule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleCacheDao {
    @Query("SELECT * FROM semester_cache WHERE userCode = :user AND isLatestKnown = 1 LIMIT 1")
    abstract fun observeSemester(user: String): Flow<SemesterCacheEntity?>

    @Query("SELECT * FROM semester_cache WHERE userCode = :user AND isLatestKnown = 1 LIMIT 1")
    abstract suspend fun semester(user: String): SemesterCacheEntity?

    @Query("UPDATE semester_cache SET isLatestKnown = 0 WHERE userCode = :user")
    protected abstract suspend fun clearLatest(user: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSemester(entity: SemesterCacheEntity)

    @Transaction
    open suspend fun saveSemester(entity: SemesterCacheEntity) {
        clearLatest(entity.userCode)
        insertSemester(entity)
    }

    @Query("SELECT * FROM schedule_week_cache WHERE userCode = :user AND schoolYear = :year AND schoolTerm = :term AND weekNumber = :week")
    abstract fun observeWeek(user: String, year: String, term: String, week: Int): Flow<ScheduleWeekCacheEntity?>

    @Query("SELECT * FROM schedule_week_cache WHERE userCode = :user AND schoolYear = :year AND schoolTerm = :term AND weekNumber = :week")
    abstract suspend fun week(user: String, year: String, term: String, week: Int): ScheduleWeekCacheEntity?

    @Query("SELECT * FROM schedule_week_cache WHERE userCode = :user AND schoolYear = :year AND schoolTerm = :term")
    abstract suspend fun weeks(user: String, year: String, term: String): List<ScheduleWeekCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveWeek(entity: ScheduleWeekCacheEntity)
}
