package com.miara.cuentame.core.database.converter

import androidx.room.TypeConverter
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
}
