package com.miara.cuentame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.miara.cuentame.core.database.entity.RestaurantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants WHERE deletedAt IS NULL LIMIT 1")
    fun observeRestaurant(): Flow<RestaurantEntity?>

    @Query("SELECT * FROM restaurants WHERE deletedAt IS NULL LIMIT 1")
    suspend fun getRestaurant(): RestaurantEntity?

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun getById(id: String): RestaurantEntity?

    @Insert
    suspend fun insert(restaurant: RestaurantEntity)

    @Update
    suspend fun update(restaurant: RestaurantEntity)

    @Query("UPDATE restaurants SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)
}
