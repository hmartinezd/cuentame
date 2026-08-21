package com.venkoi.restaurantops.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.domain.service.ReportingPeriodCalculator
import com.venkoi.restaurantops.core.model.dashboard.DashboardDateRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

class FixedTimeProvider(private val time: Instant) : com.venkoi.restaurantops.core.common.time.TimeProvider {
    override fun now(): Instant = time
}

@RunWith(AndroidJUnit4::class)
class RoomDashboardRepositoryIntegrationTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomDashboardRepository
    private val restaurantId = RestaurantId("rest_1")
    private val timeProvider = FixedTimeProvider(Instant.parse("2026-07-29T12:00:00Z"))

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        repository = RoomDashboardRepository(
            db.inventoryProjectionDao(),
            db.purchaseDao(),
            db.inventoryMovementDao(),
            db.stockCountDao(),
            db.ingredientDao(),
            ReportingPeriodCalculator(timeProvider)
        )

        runBlocking {
            db.restaurantDao().insert(RestaurantEntity(restaurantId.value, "Rest 1", "USD", "en-US", 0, 0, null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeDashboard_emptyDatabase_returnsZeros() = runBlocking {
        val snapshot = repository.observeDashboard(restaurantId, DashboardDateRange.LAST_30_DAYS).first()
        
        assertThat(snapshot.inventory.totalValue.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(snapshot.purchases.current.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(snapshot.waste.current.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }
}
