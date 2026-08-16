package com.miara.cuentame.core.database.converter

import androidx.room.TypeConverter
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.model.menu.CashDiscountBehavior
import java.math.BigDecimal
import java.time.Instant

class RoomTypeConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }

    @TypeConverter
    fun fromDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }

    @TypeConverter
    fun decimalToString(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun fromSupplierItemMappingKeyType(value: SupplierItemMappingKeyType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toSupplierItemMappingKeyType(value: String?): SupplierItemMappingKeyType? {
        return value?.let { SupplierItemMappingKeyType.valueOf(it) }
    }

    @TypeConverter
    fun fromInvoiceLineMatchStatus(value: InvoiceLineMatchStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toInvoiceLineMatchStatus(value: String?): InvoiceLineMatchStatus? {
        return value?.let { InvoiceLineMatchStatus.valueOf(it) }
    }

    @TypeConverter fun fromCashDiscountBehavior(value: CashDiscountBehavior?): String? = value?.name
    @TypeConverter fun toCashDiscountBehavior(value: String?): CashDiscountBehavior? = value?.let(CashDiscountBehavior::valueOf)
}
