package com.venkoi.cuentame.core.database.repository

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.database.RestaurantInventoryDatabase
import com.venkoi.cuentame.core.database.entity.*
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.inventory.SourceDocumentType
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupHardeningRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var failureBoundary: IntegrationFailureBoundary

    @Inject
    lateinit var postingCoordinator: PurchasePostingCoordinator

    @Inject
    lateinit var voidingCoordinator: PurchaseVoidingCoordinator

    @Inject
    lateinit var testStateManager: TestStateManager

    private val restaurantId = "hard_rest"
    private val ingId = "hard_ing"
    private val areaId = "hard_area"
    private val optId = "hard_opt"
    private val receiptId = "hard_receipt"

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
            (failureBoundary as? ConfigurableFailureBoundary)?.reset()
            
            val now = Instant.now().toEpochMilli()
            database.restaurantDao().insert(RestaurantEntity(restaurantId, "Hard Rest", "USD", "en-US", now, now, null))
            database.inventoryAreaDao().upsert(InventoryAreaEntity(areaId, restaurantId, "Area", "area", 0, true, now, now, null))
            database.unitDao().insertSeedUnits(com.venkoi.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            database.ingredientDao().insert(IngredientEntity(ingId, restaurantId, "Chicken", "chicken", null, "mass_lb", areaId, null, null, null, true, now, now, null))
            database.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity(optId, ingId, "Pound", "lb", null, BigDecimal.ONE, true, true, true, true, now, now, null))
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            (failureBoundary as? ConfigurableFailureBoundary)?.reset()
            testStateManager.resetAll()
        }
    }

    private fun seedDraftReceipt() = runBlocking {
        val now = Instant.now().toEpochMilli()
        database.purchaseDao().insertReceipt(
            PurchaseReceiptEntity(
                id = receiptId, restaurantId = restaurantId, supplierId = null, invoiceNumber = "INV-1",
                purchaseDate = now, status = DocumentStatus.DRAFT.name, notes = null, attachmentPath = null,
                attachmentDisplayName = null, createdAt = now, updatedAt = now, postedAt = null, voidedAt = null
            )
        )
        database.purchaseDao().insertLine(
            PurchaseLineEntity(
                id = "line_1", purchaseReceiptId = receiptId, ingredientId = ingId, areaId = areaId,
                ingredientUnitOptionId = optId, quantityEntered = "10.0", quantityBase = "10.0",
                lineTotal = "100.0", unitCostBase = "10.0", notes = null, createdAt = now, updatedAt = now
            )
        )
    }

    private fun seedPostedReceipt() = runBlocking {
        seedDraftReceipt()
        val rest = database.restaurantDao().getById(restaurantId)!!
        postingCoordinator.post(PurchaseReceiptId(receiptId), rest)
    }

    private fun assertRollback() = runBlocking {
        val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
        val projections = database.inventoryProjectionDao().getBalance(ingId, areaId)
        val costProj = database.ingredientCostProjectionDao().getCost(ingId)
        
        assertThat(movements).isEmpty()
        assertThat(projections).isNull()
        assertThat(costProj).isNull()
    }

    @Test
    fun purchasePost_failure_afterMovements_rollsBack() = runBlocking {
        seedDraftReceipt()
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_POST_AFTER_MOVEMENTS)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    postingCoordinator.post(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.DRAFT.name)
        assertThat(receipt?.postedAt).isNull()
        assertRollback()
        assertThat(config.triggerCount).isEqualTo(1)
    }

    @Test
    fun purchasePost_failure_afterProjections_rollsBack() = runBlocking {
        seedDraftReceipt()
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_POST_AFTER_PROJECTIONS)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    postingCoordinator.post(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.DRAFT.name)
        assertRollback()
        assertThat(config.triggerCount).isEqualTo(1)
    }

    @Test
    fun purchasePost_failure_afterMarkPosted_rollsBack() = runBlocking {
        seedDraftReceipt()
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_POST_AFTER_MARK_POSTED)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    postingCoordinator.post(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.DRAFT.name)
        assertRollback()
        assertThat(config.triggerCount).isEqualTo(1)
    }

    @Test
    fun purchaseVoid_failure_afterReversals_rollsBack() = runBlocking {
        seedPostedReceipt()
        val originalReceipt = database.purchaseDao().getReceiptById(receiptId)!!
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_VOID_AFTER_REVERSALS)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    voidingCoordinator.void(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.POSTED.name)
        assertThat(receipt?.voidedAt).isNull()
        assertThat(receipt?.updatedAt).isEqualTo(originalReceipt.updatedAt)
        
        val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
        assertThat(movements).hasSize(1) // Only original PURCHASE
        
        val balance = database.inventoryProjectionDao().getBalance(ingId, areaId)
        assertThat(balance?.quantityBase).isEqualTo("10.0")
        
        assertThat(config.triggerCount).isEqualTo(1)
    }

    @Test
    fun purchaseVoid_failure_afterProjections_rollsBack() = runBlocking {
        seedPostedReceipt()
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_VOID_AFTER_PROJECTIONS)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    voidingCoordinator.void(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.POSTED.name)
        val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
        assertThat(movements).hasSize(1)
        assertThat(config.triggerCount).isEqualTo(1)
    }

    @Test
    fun purchaseVoid_failure_afterMarkVoided_rollsBack() = runBlocking {
        seedPostedReceipt()
        val config = failureBoundary as ConfigurableFailureBoundary
        config.triggerOn(IntegrationFailurePoints.PURCHASE_VOID_AFTER_MARK_VOIDED)
        
        val rest = database.restaurantDao().getById(restaurantId)!!
        assertThrows(ForcedFailureException::class.java) {
            runBlocking {
                database.withTransaction {
                    voidingCoordinator.void(PurchaseReceiptId(receiptId), rest)
                }
            }
        }
        
        val receipt = database.purchaseDao().getReceiptById(receiptId)
        assertThat(receipt?.status).isEqualTo(DocumentStatus.POSTED.name)
        val movements = database.inventoryMovementDao().getBySourceDocument(SourceDocumentType.PURCHASE_RECEIPT.name, receiptId)
        assertThat(movements).hasSize(1)
        assertThat(config.triggerCount).isEqualTo(1)
    }
}
