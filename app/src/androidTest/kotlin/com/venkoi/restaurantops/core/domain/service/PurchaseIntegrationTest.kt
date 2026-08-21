package com.venkoi.restaurantops.core.domain.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.dao.PurchaseDao
import com.venkoi.restaurantops.core.database.entity.PurchaseReceiptEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

class PurchaseIntegrationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var purchaseDao: PurchaseDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        purchaseDao = db.purchaseDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadPurchase() = runTest {
        db.restaurantDao().insert(
            RestaurantEntity("r1", "Rest", "USD", "en-US", 0L, 0L, null)
        )

        val now = Instant.now().toEpochMilli()
        val receipt = PurchaseReceiptEntity(
            id = "p1",
            restaurantId = "r1",
            supplierId = null,
            invoiceNumber = "INV-123",
            purchaseDate = now,
            status = DocumentStatus.DRAFT.name,
            notes = null,
            attachmentPath = null,
            attachmentDisplayName = null,
            createdAt = now,
            updatedAt = now,
            postedAt = null,
            voidedAt = null
        )
        purchaseDao.insertReceipt(receipt)
        
        val loaded = purchaseDao.getReceiptById("p1")
        assertThat(loaded).isNotNull()
        assertThat(loaded?.invoiceNumber).isEqualTo("INV-123")
    }
}
