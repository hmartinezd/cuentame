package com.venkoi.restaurantops.core.database.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.common.time.TimeProvider
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.IngredientCategoryEntity
import com.venkoi.restaurantops.core.database.entity.IngredientEntity
import com.venkoi.restaurantops.core.database.entity.RestaurantEntity
import com.venkoi.restaurantops.core.domain.service.StarterCatalogSeedResult
import com.venkoi.restaurantops.core.model.catalog.CubanFoodiesStarterCatalog
import com.venkoi.restaurantops.core.model.catalog.StarterItemDefinition

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID


@RunWith(AndroidJUnit4::class)
class StarterCatalogSeederIntegrationTest {

    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var seeder: RoomStarterCatalogSeeder
    private val timeProvider = mockk<TimeProvider>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        
        // Seed system units
        SystemUnitSeeder.seed(db.openHelper.writableDatabase)
        
        seeder = RoomStarterCatalogSeeder(db, timeProvider)
        every { timeProvider.now() } returns Instant.ofEpochMilli(1000L)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedNewRestaurant_isSuccessfulAndIdempotent() = runBlocking {
        val restaurantId = "rest-1"
        db.restaurantDao().insert(RestaurantEntity(restaurantId, "Cuban Foodies", "USD", "en-US", 1000, 1000, null))

        // First run
        val result1 = seeder.seedNewRestaurant(restaurantId, CubanFoodiesStarterCatalog.definition)
        assertThat(result1).isInstanceOf(StarterCatalogSeedResult.Success::class.java)
        val success1 = result1 as StarterCatalogSeedResult.Success
        assertThat(success1.categoriesInserted).isEqualTo(9)
        assertThat(success1.ingredientsInserted).isEqualTo(89)
        assertThat(success1.unitOptionsInserted).isEqualTo(97) // 89 base + 8 packages

        // Verify some data
        val categories = db.ingredientCategoryDao().observeAllCategories().first()
        assertThat(categories).hasSize(9)
        
        val ingredients = db.ingredientDao().getAllIngredients(restaurantId)
        assertThat(ingredients).hasSize(89)

        // Second run
        val result2 = seeder.seedNewRestaurant(restaurantId, CubanFoodiesStarterCatalog.definition)
        assertThat(result2).isInstanceOf(StarterCatalogSeedResult.Success::class.java)
        val success2 = result2 as StarterCatalogSeedResult.Success
        assertThat(success2.categoriesInserted).isEqualTo(0)
        assertThat(success2.categoriesReused).isEqualTo(9)
        assertThat(success2.ingredientsInserted).isEqualTo(0)
        assertThat(success2.ingredientsSkipped).isEqualTo(89)
        assertThat(success2.unitOptionsInserted).isEqualTo(0)

        // Totals remain same
        assertThat(db.ingredientCategoryDao().observeAllCategories().first()).hasSize(9)
        assertThat(db.ingredientDao().getAllIngredients(restaurantId)).hasSize(89)
    }

    @Test
    fun seedNewRestaurant_preservesExistingUserData() = runBlocking {
        val restaurantId = "rest-1"
        db.restaurantDao().insert(RestaurantEntity(restaurantId, "Cuban Foodies", "USD", "en-US", 1000, 1000, null))

        // Pre-seed an existing category and ingredient
        val catId = "existing-cat"
        db.ingredientCategoryDao().upsert(
            IngredientCategoryEntity(catId, restaurantId, "Produce", "produce", 0, true, 500, 500, null)
        )
        val ingId = "existing-ing"
        db.ingredientDao().insert(
            IngredientEntity(ingId, restaurantId, "Zucchini", "zucchini", catId, "count_each", null, "USER-SKU", "User notes", null, true, 500, 500, null)
        )

        val result = seeder.seedNewRestaurant(restaurantId, CubanFoodiesStarterCatalog.definition)
        assertThat(result).isInstanceOf(StarterCatalogSeedResult.Success::class.java)
        val success = result as StarterCatalogSeedResult.Success
        
        assertThat(success.categoriesReused).isEqualTo(1)
        assertThat(success.ingredientsSkipped).isEqualTo(1)

        // Verify existing data was NOT overwritten
        val zucchini = db.ingredientDao().findByNormalizedName(restaurantId, "zucchini")!!
        assertThat(zucchini.id).isEqualTo(ingId)
        assertThat(zucchini.sku).isEqualTo("USER-SKU")
        assertThat(zucchini.notes).isEqualTo("User notes")
        assertThat(zucchini.baseUnitId).isEqualTo("count_each") // Catalog says mass_lb, but we matched on name and kept user unit.
    }

    @Test
    fun seedNewRestaurant_isTransactional() = runBlocking {
        val restaurantId = "rest-1"
        
        // To force a failure, we use a database without units seeded.
        // This will cause a foreign key violation on baseUnitId (mass_lb) when inserting ingredients.
        
        val dbNoUnits = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), 
            RestaurantInventoryDatabase::class.java
        ).build()
        dbNoUnits.restaurantDao().insert(RestaurantEntity(restaurantId, "Fail", "USD", "en-US", 1000, 1000, null))
        
        val seederFail = RoomStarterCatalogSeeder(dbNoUnits, timeProvider)
        
        val result = seederFail.seedNewRestaurant(restaurantId, CubanFoodiesStarterCatalog.definition)
        
        assertThat(result).isInstanceOf(StarterCatalogSeedResult.Failure::class.java)
        
        // Verify no ingredients were committed (transaction rolled back)
        assertThat(dbNoUnits.ingredientDao().getAllIngredients(restaurantId)).isEmpty()
        dbNoUnits.close()
    }



    @Test
    fun seedNewRestaurant_isolatesRestaurants() = runBlocking {
        val r1 = "rest-1"
        val r2 = "rest-2"
        db.restaurantDao().insert(RestaurantEntity(r1, "Rest 1", "USD", "en-US", 1000, 1000, null))
        db.restaurantDao().insert(RestaurantEntity(r2, "Rest 2", "USD", "en-US", 1000, 1000, null))

        seeder.seedNewRestaurant(r1, CubanFoodiesStarterCatalog.definition)
        seeder.seedNewRestaurant(r2, CubanFoodiesStarterCatalog.definition)

        val ingredients1 = db.ingredientDao().getAllIngredients(r1)
        val ingredients2 = db.ingredientDao().getAllIngredients(r2)

        assertThat(ingredients1).hasSize(89)
        assertThat(ingredients2).hasSize(89)

        val ids1 = ingredients1.map { it.id }.toSet()
        val ids2 = ingredients2.map { it.id }.toSet()

        assertThat(ids1.intersect(ids2)).isEmpty()
    }
}
