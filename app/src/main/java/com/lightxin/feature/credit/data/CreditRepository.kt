package com.lightxin.feature.credit.data

import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.CreditRetrofit
import com.lightxin.feature.credit.domain.CreditModule
import com.lightxin.feature.credit.domain.CreditOverview
import com.lightxin.feature.credit.domain.CreditRecord
import com.lightxin.feature.credit.domain.CreditRecordDetail
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditRepository @Inject constructor(
    private val api: CreditApi,
    private val tokenManager: TokenManager,
) {
    suspend fun getOverview(): Result<CreditOverview> {
        return try {
            val code = studentCode() ?: return Result.failure(Exception("登录信息已失效"))
            val resp = api.getPersonCredit(code)
            val vo = resp.data?.personnelCreditVo ?: return Result.failure(Exception("获取学分总览失败"))
            Result.success(
                CreditOverview(
                    totalCredit = vo.countCredit?.toDoubleOrNull() ?: 0.0,
                    pass = vo.pass ?: false,
                    modules = vo.list.orEmpty().map {
                        CreditModule(
                            name = it.name.orEmpty(),
                            type = it.type.orEmpty(),
                            credit = it.credit?.toDoubleOrNull() ?: 0.0,
                        )
                    },
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取学分总览", e), e))
        }
    }

    suspend fun getRecords(): Result<List<CreditRecord>> {
        return try {
            val code = studentCode() ?: return Result.failure(Exception("登录信息已失效"))
            val resp = api.getCreditRecords(code)
            Result.success(
                resp.rows.orEmpty().map {
                    CreditRecord(
                        id = it.id.orEmpty(),
                        name = it.name.orEmpty(),
                        score = it.score ?: 0.0,
                        statusName = it.statusName.orEmpty(),
                    )
                },
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取学分记录", e), e))
        }
    }

    suspend fun getRecordDetail(id: String): Result<CreditRecordDetail> {
        return try {
            val code = studentCode() ?: return Result.failure(Exception("登录信息已失效"))
            val resp = api.getCreditRecordDetail(id, code)
            val item = resp.rows?.firstOrNull() ?: return Result.failure(Exception("详情不存在"))
            Result.success(
                CreditRecordDetail(
                    name = item.name.orEmpty(),
                    highestLevelName = item.highestLevelName.orEmpty(),
                    awardLevelName = item.awardLevelName.orEmpty(),
                    awardPrizeName = item.awardPrizeName.orEmpty(),
                    prizeScore = item.prizeScore ?: 0.0,
                    getTime = item.getTime.orEmpty(),
                    qualityModuleName = item.qualityModuleName.orEmpty(),
                    qualityCategoryName = item.qualityCategoryName.orEmpty(),
                    statusName = item.statusName.orEmpty(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取记录详情", e), e))
        }
    }

    private suspend fun studentCode(): String? =
        tokenManager.getUserCode()?.takeIf { it.isNotBlank() }

    private fun mapError(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "登录已失效，请重新登录"
                error.code() >= 500 -> "${action}接口暂时异常"
                else -> "${action}失败（HTTP ${error.code()}）"
            }
            is IOException -> "网络异常，请检查连接"
            else -> error.message ?: "${action}失败"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CreditModule {

    @Provides
    @Singleton
    fun provideCreditApi(@CreditRetrofit retrofit: Retrofit): CreditApi =
        retrofit.create(CreditApi::class.java)
}
