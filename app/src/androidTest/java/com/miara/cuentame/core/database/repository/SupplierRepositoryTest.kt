package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.factory.TestFactories
import com.miara.cuentame.core.domain.repository.CreateSupplierCommand
import com.miara.cuentame.core.domain.repository.UpdateSupplierCommand
import com.miara.cuentame.core.domain.validation.ValidationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SupplierRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomSupplierRepository
    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-01-01T00:00:00Z")
    }
    private var idCounter = 0
    private val idGenerator = object : IdGenerator {
        override fun newId(): String = "id_${++idCounter}"
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomSupplierRepository(
            db.supplierDao(), idGenerator, timeProvider
        )
        
        runBlocking {
            db.restaurantDao().insert(TestFactories.createRestaurant())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createSupplier_succeeds() {
        runBlocking {
            val restId = RestaurantId("rest_1")
            val command = CreateSupplierCommand(restId, "Restaurant Depot", "1234567", "test@test.com", "Some notes")
            val id = repository.createSupplier(command)

            val saved = repository.getSupplier(id)
            assertThat(saved).isNotNull()
            assertThat(saved?.name).isEqualTo("Restaurant Depot")
            assertThat(saved?.phone).isEqualTo("1234567")
            assertThat(saved?.email).isEqualTo("test@test.com")
            assertThat(saved?.notes).isEqualTo("Some notes")
            assertThat(saved?.isActive).isTrue()
        }
    }

    @Test
    fun createSupplier_failsOnDuplicateName() {
        runBlocking {
            val restId = RestaurantId("rest_1")
            repository.createSupplier(CreateSupplierCommand(restId, "Depot"))

            assertThrows(ValidationError.SupplierNameAlreadyExists::class.java) {
                runBlocking {
                    repository.createSupplier(CreateSupplierCommand(restId, "  DEPOT  "))
                }
            }
        }
    }

    @Test
    fun updateSupplier_updatesAllowedFieldsOnly() {
        runBlocking {
            val restId = RestaurantId("rest_1")
            val id = repository.createSupplier(CreateSupplierCommand(restId, "Old Name"))

            val command = UpdateSupplierCommand(id, "New Name", "7654321", "new@test.com", "New notes")
            repository.updateSupplier(command)

            val saved = repository.getSupplier(id)!!
            assertThat(saved.name).isEqualTo("New Name")
            assertThat(saved.phone).isEqualTo("7654321")
            assertThat(saved.email).isEqualTo("new@test.com")
            assertThat(saved.notes).isEqualTo("New notes")
            assertThat(saved.restaurantId).isEqualTo(restId)
        }
    }

    @Test
    fun archiveSupplier_removesFromActiveList() {
        runBlocking {
            val restId = RestaurantId("rest_1")
            val id = repository.createSupplier(CreateSupplierCommand(restId, "To Archive"))

            repository.archiveSupplier(id, timeProvider.now())

            val activeSuppliers = repository.observeSuppliers(restId, false).first()
            assertThat(activeSuppliers.any { it.id == id }).isFalse()

            val allSuppliers = repository.observeSuppliers(restId, true).first()
            val archived = allSuppliers.find { it.id == id }
            assertThat(archived).isNotNull()
            assertThat(archived?.isActive).isFalse()
            assertThat(archived?.deletedAt).isNotNull()
        }
    }
}
