package com.miara.cuentame.core.database.seed

import androidx.sqlite.db.SupportSQLiteDatabase

object SystemUnitSeeder {
    fun seed(db: SupportSQLiteDatabase) {
        UnitSeeds.ALL_UNITS.forEach { unit ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO units (id, name, symbol, dimension, factorToCanonical, isSystem, sortOrder)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    unit.id,
                    unit.name,
                    unit.symbol,
                    unit.dimension,
                    unit.factorToCanonical,
                    if (unit.isSystem) 1 else 0,
                    unit.sortOrder
                )
            )
        }
    }
}
