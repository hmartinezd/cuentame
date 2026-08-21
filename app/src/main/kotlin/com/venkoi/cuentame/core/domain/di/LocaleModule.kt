package com.venkoi.cuentame.core.domain.di

import com.venkoi.cuentame.core.domain.usecase.locale.AppLocaleReconciler
import com.venkoi.cuentame.core.domain.usecase.locale.DefaultAppLocaleReconciler
import com.venkoi.cuentame.core.domain.usecase.locale.DefaultUpdateAppLocaleUseCase
import com.venkoi.cuentame.core.domain.usecase.locale.UpdateAppLocaleUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocaleModule {

    @Binds
    @Singleton
    abstract fun bindUpdateAppLocaleUseCase(
        impl: DefaultUpdateAppLocaleUseCase
    ): UpdateAppLocaleUseCase

    @Binds
    @Singleton
    abstract fun bindAppLocaleReconciler(
        impl: DefaultAppLocaleReconciler
    ): AppLocaleReconciler
}
