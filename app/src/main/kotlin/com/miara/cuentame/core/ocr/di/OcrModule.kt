package com.miara.cuentame.core.ocr.di

import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrEngine
import com.miara.cuentame.core.ocr.impl.MlKitPurchaseInvoiceOcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(
        impl: MlKitPurchaseInvoiceOcrEngine
    ): PurchaseInvoiceOcrEngine
}
