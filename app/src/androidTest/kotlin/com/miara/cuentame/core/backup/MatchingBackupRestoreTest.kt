package com.miara.cuentame.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.backup.internal.RestoreDatabaseApplier
import com.miara.cuentame.core.backup.model.BackupSnapshotDto
import com.miara.cuentame.core.backup.model.RestaurantBackupDto
import com.miara.cuentame.core.common.AppVersionProvider
import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.restaurant.Restaurant
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MatchingBackupRestoreTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var planner: BackupCreationPlanner

    @Inject
    lateinit var snapshotSource: BackupSnapshotSource

    @Inject
    lateinit var applier: RestoreDatabaseApplier

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var appVersionProvider: AppVersionProvider

    private val restId = RestaurantId("rest-1")

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun schema8BackupRoundTrip_preservesMatchingData() = runBlocking {
        // 1. Seed database with OCR, Parse, Mapping, and Matches
        seedDatabaseWithMatchingData()

        // 2. Create backup plan
        val restaurant = Restaurant(restId, "Test", "USD", "en-US", Instant.EPOCH, Instant.EPOCH)
        val snapshotResult = snapshotSource.loadSnapshot(restId.value)
        
        assertThat(appVersionProvider.databaseSchemaVersion).isEqualTo(8)
        
        val planResult = planner.createPlan(restaurant, snapshotResult)
        assertThat(planResult).isInstanceOf(BackupPlanningResult.Success::class.java)
        val plan = (planResult as BackupPlanningResult.Success).plan
        
        val snapshotDto = plan.snapshotDto
        
        // 3. Restore
        applier.replaceWithBackup(snapshotDto, plan.manifest)
        
        // 4. Verify equality
        val restoredSnapshot = snapshotSource.loadSnapshot(restId.value).dto
        
        assertThat(restoredSnapshot.supplierItemMappings).hasSize(1)
        assertThat(restoredSnapshot.purchaseInvoiceLineMatches).hasSize(1)
        
        assertThat(restoredSnapshot.supplierItemMappings[0].normalizedKey).isEqualTo("001234")
        assertThat(restoredSnapshot.purchaseInvoiceLineMatches[0].status).isEqualTo(InvoiceLineMatchStatus.CONFIRMED.name)
        
        assertThat(restoredSnapshot).isEqualTo(snapshotDto)
    }

    private suspend fun seedDatabaseWithMatchingData() {
        database.restaurantDao().insert(RestaurantEntity(restId.value, "Test", "USD", "en-US", 0, 0, null))
        database.unitDao().insertSeedUnits(listOf(UnitEntity("u1", "U", "u", "MASS", BigDecimal.ONE, true, 0)))
        
        val ing1 = "ing-1"
        database.ingredientDao().insert(IngredientEntity(ing1, restId.value, "Tomatoes", "tomatoes", null, "u1", null, null, null, null, true, 100, 100, null))
        
        val opt1 = "opt-1"
        database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(opt1, ing1, "LB", "lb", null, BigDecimal.ONE, true, true, true, true, 100, 100, null))
        
        val area1 = "area-1"
        database.inventoryAreaDao().upsert(InventoryAreaEntity(area1, restId.value, "Kitchen", "kitchen", 0, true, 100, 100, null))

        val sup1 = "sup-1"
        database.supplierDao().insert(SupplierEntity(sup1, restId.value, "SYSCO", "sysco", null, null, null, true, 100, 100, null))

        val pr1 = "pr-1"
        database.purchaseDao().insertReceipt(PurchaseReceiptEntity(pr1, restId.value, sup1, null, 1000L, "DRAFT", null, null, null, 100L, 200L, null, null))
        
        val ocr1 = "ocr-1"
        database.purchaseOcrDao().insertOcrResult(PurchaseInvoiceOcrResultEntity(ocr1, pr1, "sha256", "application/pdf", "MLKIT", 1, 1, "Full Text", 1000L))
        
        val parse1 = "parse-1"
        database.purchaseParseDao().insertParseResult(PurchaseInvoiceParseResultEntity(parse1, pr1, ocr1, "sha256", "ENGINE", 2, "{}", "{}", null, "[]", 1000L, null))
        
        val map1 = "map-1"
        database.supplierItemMappingDao().insertMapping(SupplierItemMappingEntity(map1, restId.value, sup1, SupplierItemMappingKeyType.VENDOR_CODE, "001234", "001234", "Desc", "Pkg", ing1, opt1, area1, 100L, 100L, 100L))
        
        database.purchaseInvoiceLineMatchDao().insertMatches(listOf(PurchaseInvoiceLineMatchEntity(parse1, 0, InvoiceLineMatchStatus.CONFIRMED, sup1, ing1, opt1, area1, map1, "KnownSupplierItem", 1.0f, 1000L)))
    }
}
