package com.venkoi.restaurantops.core.ocr.di

import com.venkoi.restaurantops.core.ocr.api.PurchaseInvoiceOcrEngine
import com.venkoi.restaurantops.core.ocr.impl.MlKitPurchaseInvoiceOcrEngine
import com.venkoi.restaurantops.core.ocr.parser.PurchaseInvoiceParser
import com.venkoi.restaurantops.core.ocr.parser.DeterministicPurchaseInvoiceParser
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

    @Binds
    @Singleton
    abstract fun bindPurchaseInvoiceParser(
        impl: DeterministicPurchaseInvoiceParser
    ): PurchaseInvoiceParser
}
