package com.lightxin.feature.water.data

import com.lightxin.core.network.QzxyRetrofit
import com.lightxin.feature.water.data.bt.BleTransport
import com.lightxin.feature.water.data.bt.SppTransport
import com.lightxin.feature.water.domain.WaterOrderApi
import com.lightxin.feature.water.domain.WaterOrderRunner
import com.lightxin.feature.water.domain.WaterOrderStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WaterModule {

    @Provides
    @Singleton
    fun provideQzxyApi(@QzxyRetrofit retrofit: Retrofit): QzxyApi =
        retrofit.create(QzxyApi::class.java)

    @Provides
    @Singleton
    fun provideWaterOrderApi(repository: QzxyRepository): WaterOrderApi = repository

    @Provides
    @Singleton
    fun provideWaterOrderStore(store: WaterLocalStore): WaterOrderStore = store

    @Provides
    @Singleton
    fun provideWaterOrderRunner(
        spp: SppTransport,
        ble: BleTransport,
        api: WaterOrderApi,
        store: WaterOrderStore,
    ): WaterOrderRunner = WaterOrderRunner(spp, ble, api, store)
}
