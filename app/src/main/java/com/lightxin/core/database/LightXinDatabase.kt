package com.lightxin.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lightxin.feature.schedule.data.local.ScheduleCacheDao
import com.lightxin.feature.schedule.data.local.ScheduleWeekCacheEntity
import com.lightxin.feature.schedule.data.local.SemesterCacheEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [SemesterCacheEntity::class, ScheduleWeekCacheEntity::class], version = 1, exportSchema = true)
abstract class LightXinDatabase : RoomDatabase() {
    abstract fun scheduleCacheDao(): ScheduleCacheDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LightXinDatabase =
        Room.databaseBuilder(context, LightXinDatabase::class.java, "lightxin_schedule.db").build()
}
