# PROMPT MAESTRO: MVP ANDROID LOCAL DE INVENTARIO PARA RESTAURANTES

Actúa como un **Senior/Staff Android Engineer y Software Architect** especializado en Kotlin, Jetpack Compose, Room, arquitectura offline-first, modelado de inventarios y aplicaciones empresariales.

Debes diseñar e implementar una aplicación Android funcional de inventario para restaurantes. Esta será la primera versión de un producto comercial, pero funcionará completamente de manera local, sin backend, sin autenticación remota y sin integraciones con POS.

No te limites a ofrecer ejemplos o pseudocódigo. Debes crear código compilable, pruebas automatizadas, documentación y una estructura mantenible.

---

# 1. Forma de trabajo

Antes de modificar el proyecto:

1. Inspecciona el repositorio existente.
2. Identifica la estructura actual, package name, Gradle, versiones y archivos disponibles.
3. Si el repositorio está vacío, crea un proyecto Android nuevo.
4. Escribe un plan de implementación en:

```text
docs/implementation-plan.md
```

5. Incluye en ese documento:

    * Arquitectura.
    * Módulos.
    * Modelo de datos.
    * Fases.
    * Riesgos.
    * Decisiones técnicas.
6. Después del plan, comienza la implementación sin detenerte a pedir aprobación.
7. Implementa por fases pequeñas y compilables.
8. Ejecuta build, lint y pruebas después de cada fase importante.
9. Corrige todos los errores encontrados antes de continuar.
10. No dejes funciones críticas como pseudocódigo.
11. No utilices `TODO()` en código ejecutable.
12. Los únicos TODO permitidos deben indicar funciones futuras explícitamente fuera del alcance del MVP.
13. Al finalizar, actualiza:

* `README.md`
* `docs/architecture.md`
* `docs/database-schema.md`
* `docs/backup-format.md`

Comandos que deben ejecutarse cuando el entorno lo permita:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
./gradlew assembleDebug
```

Si no existe un emulador para las pruebas instrumentadas, ejecuta al menos las pruebas unitarias, lint y `assembleDebug`, y documenta esa limitación.

---

# 2. Objetivo del producto

Construir una aplicación Android local que permita a un restaurante:

1. Configurar el restaurante.
2. Crear áreas de almacenamiento.
3. Crear categorías de ingredientes.
4. Crear ingredientes.
5. Definir unidades base y conversiones específicas.
6. Registrar compras.
7. Realizar conteos físicos.
8. Registrar desperdicio.
9. Mantener un libro de movimientos de inventario.
10. Calcular existencias actuales.
11. Calcular el valor aproximado del inventario.
12. Comparar dos conteos.
13. Mostrar uso no clasificado entre conteos.
14. Exportar los datos.
15. Crear y restaurar backups.
16. Funcionar sin conexión a internet.

El propósito inicial no es reemplazar un POS ni calcular todavía el consumo teórico basado en ventas y recetas.

---

# 3. Alcance funcional

## Incluido en el MVP

* Una instalación local.
* Un restaurante por instalación.
* Una o varias áreas físicas.
* Ingredientes.
* Categorías.
* Unidades y conversiones.
* Proveedores.
* Compras manuales.
* Conteos físicos.
* Desperdicio.
* Movimientos de inventario.
* Existencias actuales.
* Costo promedio.
* Reportes básicos.
* Exportación CSV.
* Backup y restauración JSON/ZIP.
* Fotografías opcionales para facturas y desperdicio.
* Inglés y español.
* Teléfonos y tablets.
* Modo claro y oscuro.

## Fuera del alcance

No implementar:

* Backend.
* API REST.
* PostgreSQL.
* Autenticación remota.
* Usuarios remotos.
* Sincronización entre dispositivos.
* Clover.
* Square.
* Toast.
* Webhooks.
* Pagos.
* Web app.
* Recetas.
* Ventas.
* OCR automático.
* Inteligencia artificial.
* Purchase orders.
* Integración contable.
* Billing.
* Suscripciones.
* Notificaciones push.
* Multi-location remoto.
* Roles complejos.
* Escáner de códigos de barras.
* Básculas Bluetooth.

No agregues permisos de internet si no son necesarios.

---

# 4. Stack técnico

Utiliza versiones estables y compatibles. No uses versiones alpha, beta o release candidate salvo que sean absolutamente necesarias.

## Requisitos

* Kotlin.
* Gradle Kotlin DSL.
* Version Catalog mediante `libs.versions.toml`.
* Java 17.
* Android SDK estable más reciente disponible.
* `minSdk 26`, salvo que el proyecto existente requiera otro mínimo.
* Jetpack Compose.
* Material 3.
* Navigation Compose.
* Lifecycle ViewModel.
* StateFlow.
* Coroutines.
* Room.
* KSP.
* Hilt para dependency injection.
* Preferences DataStore.
* Kotlinx Serialization.
* Coil para imágenes locales.
* AndroidX Activity Result APIs.
* Storage Access Framework.
* JUnit.
* Kotlin Coroutines Test.
* Turbine.
* Truth o assertions equivalentes.
* Room Testing.
* Compose UI Testing.
* Hilt Testing.

Configura:

* Room schema export.
* Pruebas de migraciones.
* Lint.
* Formato consistente.
* Detekt o ktlint si puede configurarse sin volver el proyecto innecesariamente complejo.

---

# 5. Organización del proyecto

Usa una arquitectura modular razonable, sin microfragmentar excesivamente.

Estructura recomendada:

```text
:app

:core:common
:core:model
:core:domain
:core:database
:core:designsystem
:core:testing

:feature:onboarding
:feature:dashboard
:feature:ingredients
:feature:counts
:feature:purchases
:feature:waste
:feature:reports
:feature:settings
```

Si el repositorio existente es pequeño y una estructura multimódulo dificulta innecesariamente el desarrollo, se permite mantener un solo módulo Android, pero debe conservarse una separación clara por paquetes:

```text
app/
core/
    common/
    model/
    domain/
    database/
    designsystem/
feature/
    onboarding/
    dashboard/
    ingredients/
    counts/
    purchases/
    waste/
    reports/
    settings/
```

Documenta la decisión.

---

# 6. Capas de arquitectura

Utiliza esta dirección de dependencias:

```text
Compose UI
    ↓
ViewModel
    ↓
Use Cases
    ↓
Repository Interfaces
    ↓
Repository Implementations
    ↓
Room DAOs
    ↓
Room Database
```

Reglas:

* Los ViewModels no pueden acceder directamente a DAOs.
* Los composables no pueden acceder a repositorios.
* El módulo de dominio no debe depender de Android.
* Los modelos de dominio no deben tener anotaciones de Room.
* Las entidades Room no deben utilizarse directamente en la UI.
* Deben existir mappers explícitos:

```text
Room Entity ⇄ Domain Model ⇄ UI Model
```

No existe API remota en esta fase, pero la separación debe permitir agregarla posteriormente.

No implementes DTOs de red ni clases remotas vacías.

---

# 7. Convenciones de código

* Identificadores, nombres de clases, nombres de funciones y comentarios técnicos en inglés.
* Textos visibles de UI mediante recursos.
* Recursos en inglés y español.
* Modelos inmutables.
* Evitar `var` excepto cuando sea estrictamente necesario.
* No usar `GlobalScope`.
* No bloquear el main thread.
* Inyectar dispatchers cuando facilite las pruebas.
* Utilizar `Flow` para observación de datos.
* Utilizar `StateFlow` para estados de ViewModel.
* Utilizar `collectAsStateWithLifecycle`.
* Utilizar eventos de una sola vez mediante `Channel` o `SharedFlow`, evitando repetir eventos en recomposición.
* Utilizar `SavedStateHandle` cuando corresponda.
* No colocar lógica de negocio compleja en composables.
* No usar `Float` o `Double` para cantidades, costos o dinero.
* Utilizar `BigDecimal` en el dominio.
* Definir reglas explícitas de redondeo.
* Usar `RoundingMode.HALF_UP` para cantidades monetarias mostradas.
* Guardar fechas como `Instant` en el dominio.
* Persistir fechas como epoch milliseconds.
* Utilizar UUID generados localmente.

---

# 8. IDs y futura migración

Todos los registros de negocio deben utilizar IDs UUID generados en el cliente.

Ejemplo:

```kotlin
UUID.randomUUID().toString()
```

No utilizar IDs autoincrementales para entidades de negocio.

Los IDs deben mantenerse sin cambios durante toda la vida del registro.

Cada entidad principal debe incluir, cuando aplique:

```text
id
restaurantId
createdAt
updatedAt
deletedAt
```

`deletedAt` permite soft delete.

No implementar todavía:

```text
syncStatus
serverVersion
remoteId
pendingUpload
```

Esos campos solo se añadirán cuando exista sincronización real.

---

# 9. Persistencia decimal

Room y SQLite no deben realizar cálculos con `Double`.

En el dominio:

```kotlin
BigDecimal
```

En Room:

* Persistir valores decimales como strings canónicos.
* Usar `BigDecimal.toPlainString()`.
* Crear `TypeConverter<BigDecimal, String>`.
* Nunca persistir números usando formato dependiente del locale.
* Los valores deben usar punto decimal internamente.

Los cálculos y agregaciones pueden realizarse en Kotlin porque el volumen de datos del MVP será limitado.

Para dinero:

* Representar importes mediante `BigDecimal`.
* Asociarlos al `currencyCode` del restaurante.
* Mostrar normalmente dos decimales.
* Conservar mayor precisión para costos unitarios.

---

# 10. Modelo de unidades

Debe existir una distinción entre:

1. Unidades físicas estándar.
2. Unidades específicas de un ingrediente o empaque.

## Dimensiones

```kotlin
enum class UnitDimension {
    MASS,
    VOLUME,
    COUNT
}
```

## Unidades canónicas

* MASS: gram.
* VOLUME: milliliter.
* COUNT: each.

## Unidades iniciales

Sembrar las siguientes unidades con IDs estables:

### Mass

| ID      | Nombre   | Símbolo | Factor a gramos |
| ------- | -------- | ------- | --------------: |
| mass_g  | Gram     | g       |               1 |
| mass_kg | Kilogram | kg      |            1000 |
| mass_oz | Ounce    | oz      |    28.349523125 |
| mass_lb | Pound    | lb      |       453.59237 |

### Volume

| ID               | Nombre      | Símbolo |    Factor a ml |
| ---------------- | ----------- | ------- | -------------: |
| volume_ml        | Milliliter  | ml      |              1 |
| volume_l         | Liter       | L       |           1000 |
| volume_tsp_us    | Teaspoon    | tsp     |  4.92892159375 |
| volume_tbsp_us   | Tablespoon  | tbsp    | 14.78676478125 |
| volume_fl_oz_us  | Fluid ounce | fl oz   |  29.5735295625 |
| volume_cup_us    | Cup         | cup     |    236.5882365 |
| volume_pint_us   | Pint        | pt      |     473.176473 |
| volume_quart_us  | Quart       | qt      |     946.352946 |
| volume_gallon_us | Gallon      | gal     |    3785.411784 |

### Count

| ID         | Nombre | Símbolo | Factor |
| ---------- | ------ | ------- | -----: |
| count_each | Each   | ea      |      1 |

Una conversión estándar solo puede ocurrir entre unidades de la misma dimensión.

---

# 11. Unidades específicas por ingrediente

Cada ingrediente debe tener una unidad base.

Ejemplo:

```text
Chicken breast
Base unit: lb
```

Debe existir una tabla de opciones de unidad por ingrediente:

```text
ingredient_unit_options
```

Ejemplos:

```text
lb                  = 1 lb
oz                  = 0.0625 lb
case 40 lb          = 40 lb
package 5 lb        = 5 lb
```

Una caja no tiene una conversión universal. La conversión pertenece al ingrediente.

Campos:

```text
id
ingredientId
displayName
shortLabel
standardUnitId nullable
factorToBase
isBase
isDefaultCount
isDefaultPurchase
isActive
createdAt
updatedAt
deletedAt
```

Reglas:

* `factorToBase` debe ser mayor que cero.
* Debe existir exactamente una opción base activa por ingrediente.
* La opción base tiene factor `1`.
* Si se utiliza una unidad estándar compatible, el factor puede calcularse mediante los factores canónicos.
* Una unidad personalizada puede no tener `standardUnitId`.
* Un ingrediente puede tener múltiples empaques.
* La UI debe mostrar ejemplos claros como:

```text
Case — 40 lb
Package — 5 lb
Ounce — 0.0625 lb
```

---

# 12. Esquema de base de datos

Utiliza Room.

Configura:

```kotlin
@Database(
    entities = [...],
    version = 1,
    exportSchema = true
)
```

Nunca utilizar:

```kotlin
fallbackToDestructiveMigration()
```

## 12.1 RestaurantEntity

Tabla:

```text
restaurants
```

Campos:

```text
id: String PK
name: String
currencyCode: String
localeTag: String
createdAt: Long
updatedAt: Long
deletedAt: Long?
```

Aunque exista un restaurante por instalación, conservar la tabla y el `restaurantId` en las demás entidades.

---

## 12.2 InventoryAreaEntity

Tabla:

```text
inventory_areas
```

Campos:

```text
id: String PK
restaurantId: String FK
name: String
normalizedName: String
sortOrder: Int
isActive: Boolean
createdAt: Long
updatedAt: Long
deletedAt: Long?
```

Ejemplos:

* Walk-in Cooler.
* Freezer.
* Dry Storage.
* Bar.
* Prep Area.
* Kitchen Line.

Índices:

```text
restaurantId
restaurantId + normalizedName
sortOrder
```

---

## 12.3 IngredientCategoryEntity

Tabla:

```text
ingredient_categories
```

Campos:

```text
id
restaurantId
name
normalizedName
sortOrder
isActive
createdAt
updatedAt
deletedAt
```

Ejemplos:

* Meat.
* Dairy.
* Produce.
* Dry Goods.
* Beverages.
* Alcohol.
* Cleaning Supplies.

---

## 12.4 UnitEntity

Tabla:

```text
units
```

Campos:

```text
id: String PK
name: String
symbol: String
dimension: UnitDimension
factorToCanonical: BigDecimal persisted as String
isSystem: Boolean
sortOrder: Int
```

Las unidades del sistema se insertan mediante callback o prepackaged data de manera idempotente.

---

## 12.5 IngredientEntity

Tabla:

```text
ingredients
```

Campos:

```text
id
restaurantId
name
normalizedName
categoryId nullable
baseUnitId
defaultAreaId nullable
sku nullable
notes nullable
reorderPointBase nullable
isActive
createdAt
updatedAt
deletedAt
```

Índices:

```text
restaurantId
categoryId
defaultAreaId
baseUnitId
restaurantId + normalizedName
```

Reglas:

* El nombre es obligatorio.
* La unidad base es obligatoria.
* La unidad base no puede cambiar cuando existan movimientos publicados, salvo mediante una futura operación de migración explícita.
* Archivar no elimina el historial.
* Un ingrediente archivado no debe aparecer por defecto en compras o conteos nuevos.

---

## 12.6 IngredientUnitOptionEntity

Tabla:

```text
ingredient_unit_options
```

Campos:

```text
id
ingredientId
displayName
shortLabel
standardUnitId nullable
factorToBase
isBase
isDefaultCount
isDefaultPurchase
isActive
createdAt
updatedAt
deletedAt
```

Índices:

```text
ingredientId
standardUnitId
ingredientId + isBase
```

---

## 12.7 SupplierEntity

Tabla:

```text
suppliers
```

Campos:

```text
id
restaurantId
name
normalizedName
phone nullable
email nullable
notes nullable
isActive
createdAt
updatedAt
deletedAt
```

---

## 12.8 PurchaseReceiptEntity

Tabla:

```text
purchase_receipts
```

Campos:

```text
id
restaurantId
supplierId nullable
invoiceNumber nullable
purchaseDate
status
notes nullable
attachmentPath nullable
createdAt
updatedAt
postedAt nullable
voidedAt nullable
```

Estados:

```kotlin
enum class DocumentStatus {
    DRAFT,
    POSTED,
    VOIDED
}
```

---

## 12.9 PurchaseLineEntity

Tabla:

```text
purchase_lines
```

Campos:

```text
id
purchaseReceiptId
ingredientId
areaId
ingredientUnitOptionId
quantityEntered
quantityBase
lineTotal
unitCostBase
notes nullable
createdAt
updatedAt
```

Reglas:

```text
quantityEntered > 0
quantityBase > 0
lineTotal >= 0
unitCostBase = lineTotal / quantityBase
```

Una línea debe indicar el área donde se recibe el producto.

---

## 12.10 StockCountEntity

Tabla:

```text
stock_counts
```

Campos:

```text
id
restaurantId
name
startedAt
effectiveAt
completedAt nullable
status
notes nullable
createdAt
updatedAt
voidedAt nullable
```

Estados:

```kotlin
enum class StockCountStatus {
    DRAFT,
    COMPLETED,
    VOIDED
}
```

---

## 12.11 StockCountAreaEntity

Tabla:

```text
stock_count_areas
```

Campos:

```text
id
stockCountId
areaId
status
startedAt nullable
completedAt nullable
sortOrder
```

Estados:

```kotlin
enum class CountAreaStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}
```

---

## 12.12 StockCountLineEntity

Tabla:

```text
stock_count_lines
```

Campos:

```text
id
stockCountAreaId
ingredientId
ingredientUnitOptionId
quantityEntered
quantityBase
expectedQuantityBaseSnapshot nullable
adjustmentQuantityBase nullable
notes nullable
createdAt
updatedAt
```

Restricción lógica:

```text
stockCountAreaId + ingredientId
```

Debe existir como combinación única.

---

## 12.13 WasteEventEntity

Tabla:

```text
waste_events
```

Campos:

```text
id
restaurantId
ingredientId
areaId
ingredientUnitOptionId
quantityEntered
quantityBase
reason
effectiveAt
notes nullable
attachmentPath nullable
status
createdAt
updatedAt
postedAt nullable
voidedAt nullable
```

Motivos:

```kotlin
enum class WasteReason {
    EXPIRED,
    SPOILED,
    PREPARATION_ERROR,
    OVERPRODUCTION,
    DROPPED_OR_DAMAGED,
    CUSTOMER_RETURN,
    QUALITY_REJECTION,
    OTHER
}
```

---

## 12.14 InventoryMovementEntity

Tabla:

```text
inventory_movements
```

Esta tabla es la fuente histórica de verdad del inventario.

Campos:

```text
id
restaurantId
ingredientId
areaId
movementType
quantityBaseSigned
unitCostBaseSnapshot nullable
totalValueSnapshot nullable
effectiveAt
sourceDocumentType
sourceDocumentId
sourceLineId
reversalOfMovementId nullable
createdAt
```

Tipos:

```kotlin
enum class InventoryMovementType {
    OPENING_BALANCE,
    PURCHASE,
    WASTE,
    COUNT_ADJUSTMENT,
    MANUAL_ADJUSTMENT,
    REVERSAL
}
```

El signo de `quantityBaseSigned` debe reflejar el efecto:

```text
PURCHASE             positivo
OPENING_BALANCE      positivo o cero
WASTE                negativo
COUNT_ADJUSTMENT     positivo o negativo
MANUAL_ADJUSTMENT    positivo o negativo
REVERSAL             opuesto al movimiento original
```

Índices:

```text
restaurantId
ingredientId
areaId
effectiveAt
ingredientId + areaId + effectiveAt
sourceDocumentType + sourceLineId + movementType
reversalOfMovementId
```

Debe existir protección contra movimientos duplicados.

---

## 12.15 InventoryBalanceProjectionEntity

Tabla:

```text
inventory_balance_projection
```

Clave primaria compuesta:

```text
restaurantId
ingredientId
areaId
```

Campos:

```text
restaurantId
ingredientId
areaId
quantityBase
updatedAt
```

Esta tabla es una proyección derivada para lectura rápida.

La fuente de verdad sigue siendo `inventory_movements`.

---

## 12.16 IngredientCostProjectionEntity

Tabla:

```text
ingredient_cost_projection
```

Clave primaria:

```text
restaurantId + ingredientId
```

Campos:

```text
restaurantId
ingredientId
averageUnitCostBase
updatedAt
```

---

# 13. Foreign keys

Utiliza foreign keys e índices apropiados.

Reglas generales:

* Hijos de documentos borradores pueden usar `CASCADE`.
* Entidades maestras como ingredientes, áreas y proveedores deben usar soft delete.
* No eliminar físicamente ingredientes con historial.
* No permitir eliminar unidades referenciadas.
* Cuando un área esté referenciada, debe archivarse en lugar de eliminarse.

Todas las operaciones de publicación y anulación deben usar transacciones Room.

---

# 14. Reglas del inventario

## 14.1 Fuente de verdad

No guardar simplemente:

```text
Chicken = 42 lb
```

El inventario debe derivarse de movimientos.

Ejemplo:

```text
PURCHASE             +40 lb
WASTE                 -3 lb
COUNT_ADJUSTMENT      -2 lb
```

Resultado:

```text
35 lb
```

## 14.2 Publicar una compra

Al cambiar una compra de `DRAFT` a `POSTED`:

1. Validar todas las líneas.
2. Calcular las cantidades en unidad base.
3. Calcular costo por unidad base.
4. Crear un movimiento positivo por línea.
5. Actualizar la proyección de balance.
6. Actualizar el costo promedio.
7. Marcar el documento como `POSTED`.
8. Realizar todo dentro de una transacción.
9. La operación debe ser idempotente.

Una compra publicada es inmutable.

## 14.3 Anular una compra

No borrar los movimientos originales.

Crear movimientos `REVERSAL` con cantidad opuesta.

Después:

1. Marcar documento `VOIDED`.
2. Reconstruir las proyecciones del ingrediente afectado.
3. Mantener auditoría completa.

## 14.4 Registrar desperdicio

Al publicar desperdicio:

1. Convertir a unidad base.
2. Crear movimiento negativo.
3. Utilizar el costo promedio vigente como snapshot.
4. Actualizar balance.
5. Mantener el evento publicado e inmutable.

## 14.5 Anular desperdicio

Crear movimiento de reversión.

No modificar silenciosamente el movimiento original.

## 14.6 Completar conteo

Para cada línea:

```text
adjustment = counted quantity - expected quantity
```

Si no existe movimiento anterior para ese ingrediente y área:

```text
OPENING_BALANCE = counted quantity
```

Si existe historial:

```text
COUNT_ADJUSTMENT = counted - expected
```

Guardar en la línea:

```text
expectedQuantityBaseSnapshot
adjustmentQuantityBase
```

Después:

* Crear los movimientos.
* Actualizar balances.
* Completar áreas.
* Marcar el conteo `COMPLETED`.
* Hacerlo de forma transaccional e idempotente.

Un conteo completado no puede editarse.

## 14.7 Valores negativos

La aplicación debe permitir detectar balances negativos, pero no ocultarlos.

Mostrar una advertencia:

```text
Inventory is negative. Review purchases, counts, waste, or unit conversions.
```

No corregir automáticamente sin intervención.

---

# 15. Costo promedio ponderado

Mantener costo promedio por ingrediente.

Al publicar una compra:

```text
newAverage =
    ((currentQuantity × currentAverageCost)
    + (purchaseQuantity × purchaseUnitCost))
    / (currentQuantity + purchaseQuantity)
```

Reglas:

* Si no existe costo previo, utilizar costo de compra.
* Si el inventario actual es cero o negativo, utilizar el costo de compra como nuevo costo promedio.
* Los movimientos negativos no cambian el costo promedio.
* Los movimientos guardan un snapshot de costo.
* Después de una anulación o movimiento retroactivo, reconstruir la proyección reproduciendo cronológicamente los movimientos.
* Ordenar por:

    1. `effectiveAt`
    2. `createdAt`
    3. `id`

Crear un servicio de dominio:

```text
InventoryProjectionRebuilder
```

Debe poder reconstruir:

* Balance por ingrediente y área.
* Costo promedio por ingrediente.

---

# 16. Repositorios

Crear interfaces de dominio.

## RestaurantRepository

```kotlin
interface RestaurantRepository {
    fun observeRestaurant(): Flow<Restaurant?>
    suspend fun createRestaurant(command: CreateRestaurantCommand): Restaurant
    suspend fun updateRestaurant(restaurant: Restaurant)
}
```

## InventoryAreaRepository

```kotlin
interface InventoryAreaRepository {
    fun observeActiveAreas(): Flow<List<InventoryArea>>
    suspend fun getById(id: String): InventoryArea?
    suspend fun save(area: InventoryArea)
    suspend fun archive(id: String)
    suspend fun reorder(ids: List<String>)
}
```

## IngredientRepository

```kotlin
interface IngredientRepository {
    fun observeIngredients(query: IngredientQuery): Flow<List<IngredientSummary>>
    fun observeIngredient(id: String): Flow<Ingredient?>
    suspend fun create(command: CreateIngredientCommand): Ingredient
    suspend fun update(command: UpdateIngredientCommand)
    suspend fun archive(id: String)
    suspend fun addUnitOption(command: AddIngredientUnitOptionCommand)
    suspend fun updateUnitOption(command: UpdateIngredientUnitOptionCommand)
}
```

## SupplierRepository

## PurchaseRepository

```kotlin
interface PurchaseRepository {
    fun observePurchases(filter: PurchaseFilter): Flow<List<PurchaseSummary>>
    fun observePurchase(id: String): Flow<PurchaseDetails?>
    suspend fun createDraft(command: CreatePurchaseDraftCommand): String
    suspend fun updateDraft(command: UpdatePurchaseDraftCommand)
    suspend fun post(id: String)
    suspend fun void(id: String)
}
```

## StockCountRepository

```kotlin
interface StockCountRepository {
    fun observeCounts(): Flow<List<StockCountSummary>>
    fun observeCount(id: String): Flow<StockCountDetails?>
    suspend fun start(command: StartStockCountCommand): String
    suspend fun saveLine(command: SaveStockCountLineCommand)
    suspend fun completeArea(countAreaId: String)
    suspend fun completeCount(countId: String)
    suspend fun voidCount(countId: String)
}
```

## WasteRepository

## InventoryRepository

```kotlin
interface InventoryRepository {
    fun observeInventory(query: InventoryQuery): Flow<List<InventoryItemBalance>>
    fun observeIngredientBalance(ingredientId: String): Flow<IngredientBalanceDetails?>
    suspend fun rebuildIngredientProjection(ingredientId: String)
}
```

## BackupRepository

```kotlin
interface BackupRepository {
    suspend fun createBackup(destination: Uri): BackupResult
    suspend fun validateBackup(source: Uri): BackupValidationResult
    suspend fun restoreBackup(source: Uri): RestoreResult
}
```

---

# 17. Casos de uso

Crear al menos:

```text
CompleteOnboardingUseCase
CreateIngredientUseCase
UpdateIngredientUseCase
ArchiveIngredientUseCase
AddIngredientUnitOptionUseCase
CreateSupplierUseCase

CreatePurchaseDraftUseCase
SavePurchaseLineUseCase
PostPurchaseUseCase
VoidPurchaseUseCase

StartStockCountUseCase
SaveStockCountLineUseCase
CompleteStockCountAreaUseCase
CompleteStockCountUseCase
VoidStockCountUseCase

PostWasteEventUseCase
VoidWasteEventUseCase

ObserveDashboardUseCase
ObserveInventoryUseCase
ObserveIngredientHistoryUseCase
GenerateCountComparisonReportUseCase
GenerateWasteReportUseCase

ExportCsvUseCase
CreateBackupUseCase
RestoreBackupUseCase
```

Las reglas de negocio deben estar en casos de uso o servicios de dominio, no en ViewModels.

---

# 18. UI y navegación

Utilizar Material 3.

La UI debe ser limpia, funcional y profesional, no un prototipo visual descuidado.

## Navegación principal

Utilizar hasta cinco destinos:

```text
Home
Inventory
Count
Activity
Reports
```

Settings puede abrirse desde el menú superior.

## Home

Mostrar:

* Valor aproximado de inventario.
* Número de ingredientes activos.
* Fecha del último conteo.
* Compras del período.
* Desperdicio del período.
* Ingredientes con balance negativo.
* Botones rápidos:

    * Start count.
    * Add purchase.
    * Record waste.
    * Add ingredient.

## Inventory

Lista de ingredientes con:

* Nombre.
* Categoría.
* Área predeterminada.
* Cantidad actual.
* Unidad base.
* Costo promedio.
* Valor aproximado.
* Indicador de inventario negativo.
* Indicador de reorder point.

Funciones:

* Search.
* Filter by area.
* Filter by category.
* Active/archived filter.
* Sort by name, value, quantity or variation.

Al tocar un ingrediente, mostrar:

* Balance total.
* Balance por área.
* Costo promedio.
* Valor.
* Historial de movimientos.
* Opciones de unidad.
* Compras recientes.
* Desperdicio reciente.

## Count

Mostrar:

* Conteos en borrador.
* Último conteo completado.
* Historial.
* Botón “Start new count”.

Flujo:

```text
Select areas
    ↓
Create count
    ↓
Count by area
    ↓
Review missing items
    ↓
Complete area
    ↓
Review adjustments
    ↓
Complete count
```

Pantalla de conteo:

* Agrupada por área.
* Ingredientes en orden configurable.
* Campo numérico grande.
* Unidad seleccionable.
* Última cantidad como referencia.
* Estado: pending / counted.
* Guardado automático.
* Progreso visible.
* Búsqueda.
* Botón “Mark area complete”.

Usar teclado decimal.

Aceptar punto o coma como separador, normalizando internamente.

## Activity

Utilizar tabs o segmented buttons:

```text
Purchases
Waste
```

### Purchases

* Lista de drafts, posted y voided.
* Crear compra.
* Seleccionar proveedor.
* Fecha.
* Número de factura.
* Fotografía opcional.
* Líneas.
* Total.
* Review.
* Post.

### Waste

* Lista cronológica.
* Filtros por fecha, ingrediente, área y motivo.
* Botón de registro rápido.
* Fotografía opcional.

## Reports

Incluir:

1. Inventory value.
2. Purchases by period.
3. Waste by period.
4. Negative inventory.
5. Count comparison.
6. Unclassified usage.

---

# 19. Reporte entre conteos

Deben seleccionarse dos conteos completados.

Para cada ingrediente:

```text
initial inventory
+ purchases
- recorded waste
- final inventory
= unclassified usage
```

Ejemplo:

```text
Initial inventory:     20 lb
Purchases:             40 lb
Recorded waste:         3 lb
Final inventory:       25 lb
Unclassified usage:    32 lb
```

No llamar a esto automáticamente:

```text
theft
loss
shrinkage
```

Mostrarlo como:

```text
Unclassified usage
```

Explicar que puede incluir:

* Ventas.
* Preparación.
* Desperdicio no registrado.
* Errores de conteo.
* Conversiones incorrectas.
* Ajustes no registrados.

Mostrar también el valor estimado usando costo promedio.

---

# 20. Diseño visual

Crear un diseño visual sobrio y empresarial.

Características:

* Material 3.
* Espaciado consistente.
* Tipografía clara.
* Tarjetas informativas.
* Touch targets de al menos 48 dp.
* Campos numéricos grandes durante conteos.
* Estados visuales claros.
* No depender exclusivamente de colores.
* Buen contraste.
* Soporte light/dark.
* Dynamic color opcional.
* Diseños adaptativos para tablet.

## Teléfono

* Navegación inferior.
* Pantallas de una columna.
* Formularios en páginas separadas o bottom sheets.

## Tablet

* Navigation rail cuando sea apropiado.
* List-detail.
* Conteo con lista a la izquierda y editor a la derecha.
* Mayor densidad de información sin reducir legibilidad.

Utilizar Window Size Classes o mecanismo estable equivalente.

---

# 21. Estado de UI

Cada pantalla debe tener un `UiState` inmutable.

Ejemplo:

```kotlin
data class IngredientListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedAreaId: String? = null,
    val selectedCategoryId: String? = null,
    val ingredients: List<IngredientListItemUiModel> = emptyList(),
    val errorMessage: String? = null
)
```

Los ViewModels deben exponer:

```kotlin
val uiState: StateFlow<UiState>
```

Las acciones deben enviarse mediante funciones explícitas:

```kotlin
fun onSearchQueryChanged(value: String)
fun onAreaSelected(id: String?)
fun onIngredientClicked(id: String)
```

Evitar un único método genérico que reciba cualquier evento sin necesidad.

---

# 22. Formularios y validación

Crear modelos de formulario separados.

Validaciones:

## Ingredient

* Name required.
* Base unit required.
* Default area optional.
* Reorder point non-negative.
* No duplicate active name without warning.

## Unit option

* Name required.
* Factor greater than zero.
* Exactly one base option.
* Factor of base option equals one.

## Purchase

* At least one valid line.
* Quantity greater than zero.
* Line total non-negative.
* Area required.
* Ingredient required.
* Unit option required.

## Stock count

* Quantity non-negative.
* No duplicate ingredient in same count area.
* Show missing ingredients before completion.
* Require explicit confirmation before completion.

## Waste

* Quantity greater than zero.
* Reason required.
* Area required.
* Warn if quantity exceeds current inventory, but allow posting after confirmation.

---

# 23. Fotografías y archivos

Para fotografías:

* Usar Activity Result APIs.
* Guardar copia permanente dentro de almacenamiento privado de la app.
* No depender de permisos temporales de URI.
* Usar rutas relativas internas.
* Crear directorios:

```text
files/attachments/purchases/
files/attachments/waste/
```

* Comprimir fotografías razonablemente.
* Mantener orientación correcta.
* Utilizar Coil para mostrarlas.
* Eliminar archivos huérfanos cuando se elimine un borrador.
* No eliminar archivos asociados a documentos publicados sin una operación explícita.

---

# 24. Backup

Crear backup portable, no copiar simplemente el archivo SQLite.

Formato:

```text
restaurant_inventory_backup_YYYYMMDD_HHmm.zip
```

Contenido:

```text
manifest.json
data/restaurants.json
data/areas.json
data/categories.json
data/units.json
data/ingredients.json
data/ingredient_unit_options.json
data/suppliers.json
data/purchases.json
data/purchase_lines.json
data/stock_counts.json
data/stock_count_areas.json
data/stock_count_lines.json
data/waste_events.json
data/inventory_movements.json
attachments/...
```

`manifest.json`:

```json
{
  "formatVersion": 1,
  "createdAt": "ISO-8601",
  "appVersion": "string",
  "restaurantId": "uuid",
  "restaurantName": "string",
  "currencyCode": "USD"
}
```

Requisitos:

* Kotlinx Serialization.
* Storage Access Framework.
* Validar formato antes de restaurar.
* Mostrar resumen del backup.
* Confirmar antes de reemplazar datos.
* Crear backup automático local antes de restaurar.
* Restaurar dentro de una transacción.
* Reconstruir proyecciones después de restaurar.
* Mostrar errores claros.
* No aceptar un formato futuro desconocido sin advertencia.

---

# 25. Exportación CSV

Permitir exportar:

* Ingredients.
* Current inventory.
* Purchases.
* Waste.
* Count comparison.

Usar UTF-8.

Escapar correctamente:

* Commas.
* Quotes.
* Line breaks.

Fechas legibles y números en formato neutral.

---

# 26. Onboarding

Primera ejecución:

## Paso 1

Restaurant name.

## Paso 2

Currency y locale.

Default:

```text
USD
en-US
```

## Paso 3

Crear áreas sugeridas:

```text
Walk-in Cooler
Freezer
Dry Storage
Bar
Prep Area
Kitchen Line
```

El usuario puede marcar cuáles desea.

## Paso 4

Crear categorías sugeridas.

## Paso 5

Mostrar opción:

```text
Load demo data
Start empty
```

Guardar `onboardingCompleted` en DataStore.

---

# 27. Demo data

Crear datos de demostración opcionales:

* Un restaurante.
* Tres áreas.
* Diez ingredientes.
* Dos proveedores.
* Dos compras.
* Un conteo inicial.
* Dos eventos de desperdicio.
* Un conteo posterior.
* Movimientos y reportes consistentes.

Los datos deben demostrar:

* Unidades estándar.
* Caja a libras.
* Paquete a unidades.
* Conteo.
* Compra.
* Desperdicio.
* Uso no clasificado.

No insertar demo data en una instalación real sin consentimiento.

---

# 28. Manejo de errores

Crear errores de dominio claros.

Ejemplos:

```text
IngredientNotFound
InvalidUnitConversion
DocumentAlreadyPosted
DocumentAlreadyVoided
DuplicatePosting
CountAlreadyCompleted
MissingCountLines
InvalidQuantity
BackupVersionUnsupported
BackupCorrupted
```

Los errores esperados deben convertirse en mensajes de UI comprensibles.

Errores inesperados:

* Registrar de manera local.
* Mostrar mensaje genérico.
* No perder borradores.
* No cerrar la aplicación.

---

# 29. Pruebas

## Unit tests

Crear pruebas para:

* Conversión entre unidades.
* Conversión de empaque a unidad base.
* Validación de unidad base.
* Costo unitario.
* Costo promedio ponderado.
* Creación de movimientos.
* Publicación de compra.
* Anulación de compra.
* Publicación de desperdicio.
* Anulación de desperdicio.
* Conteo inicial.
* Conteo posterior.
* Ajustes positivos.
* Ajustes negativos.
* Reporte entre conteos.
* Balance negativo.
* Reproducción de movimientos.
* Rebuild de proyecciones.
* Redondeo monetario.
* Idempotencia.

## DAO tests

Usar Room in-memory para probar:

* Inserts.
* Updates.
* Relationships.
* Foreign keys.
* Queries.
* Flows.
* Soft deletes.
* Índices y restricciones lógicas.

## Migration tests

Aunque la primera versión sea schema 1, preparar infraestructura para probar futuras migraciones.

Exportar schema.

## ViewModel tests

Probar:

* Loading.
* Success.
* Empty states.
* Form validation.
* Error handling.
* One-time events.
* Search.
* Filters.
* Completion confirmation.

Utilizar Coroutines Test y Turbine.

## Compose tests

Probar flujos principales:

1. Onboarding.
2. Crear ingrediente.
3. Añadir empaque.
4. Registrar compra.
5. Realizar conteo.
6. Registrar desperdicio.
7. Ver reporte.
8. Crear backup.

---

# 30. Performance

No implementar Paging salvo que sea necesario.

Para el MVP:

* Room Flow.
* LazyColumn con keys estables.
* Búsqueda con debounce.
* Evitar recomposiciones innecesarias.
* No cargar imágenes completas en memoria.
* Consultas indexadas.
* Transacciones para operaciones complejas.
* Cálculos pesados en dispatcher de background.

El producto debe manejar cómodamente:

```text
2,000 ingredientes
10,000 compras
100,000 movimientos
500 conteos
10,000 eventos de desperdicio
```

No es necesario optimizar para millones de registros.

---

# 31. Accesibilidad y localización

* Todos los textos visibles en resources.
* `values/strings.xml`.
* `values-es/strings.xml`.
* Content descriptions para iconos importantes.
* Soporte de tamaño de fuente.
* No cortar textos largos.
* Contraste suficiente.
* No depender únicamente del color.
* Formatear moneda y fechas según locale.
* El modelo de datos interno debe seguir siendo locale-neutral.

---

# 32. Seguridad local

* Guardar datos en almacenamiento privado.
* No registrar datos completos del negocio innecesariamente en logs.
* No almacenar información de tarjetas.
* No incluir analytics externos en el MVP.
* No incluir ad SDKs.
* No incluir crash reporting externo salvo solicitud explícita.
* No exponer archivos mediante almacenamiento público sin acción del usuario.
* Los backups se crean únicamente mediante el Storage Access Framework.

---

# 33. Fases de implementación

## Milestone 1: Project foundation

* Proyecto.
* Gradle.
* Modules/packages.
* Compose.
* Material 3.
* Navigation.
* Hilt.
* Room.
* DataStore.
* Test infrastructure.
* Documentation.

Criterio:

```text
assembleDebug succeeds
unit tests succeed
```

## Milestone 2: Core domain and database

* Models.
* Entities.
* DAOs.
* Type converters.
* Mappers.
* Repositories.
* Unit seeds.
* Database tests.

## Milestone 3: Onboarding and settings

* Restaurant.
* Areas.
* Categories.
* Currency.
* Language.
* Demo data.

## Milestone 4: Ingredients

* List.
* Search.
* Filter.
* Create/edit.
* Units.
* Packaging.
* Archive.
* Details.

## Milestone 5: Purchases

* Suppliers.
* Draft.
* Lines.
* Posting.
* Movements.
* Cost average.
* Void.
* Tests.

## Milestone 6: Stock counts

* Create count.
* Areas.
* Autosave.
* Resume.
* Review.
* Complete.
* Opening balances.
* Adjustments.
* Tests.

## Milestone 7: Waste

* Create.
* Post.
* Void.
* Photo.
* History.
* Reports.

## Milestone 8: Dashboard and reports

* Inventory value.
* Current balances.
* Negative inventory.
* Count comparison.
* Unclassified usage.
* Purchase and waste summaries.

## Milestone 9: Backup and export

* ZIP/JSON backup.
* Validation.
* Restore.
* CSV exports.
* Documentation.

## Milestone 10: Polish

* Tablet layouts.
* Accessibility.
* Spanish.
* Empty states.
* Error states.
* UI tests.
* Lint.
* Final build.

---

# 34. Criterios de aceptación

El MVP se considera funcional cuando una persona puede:

1. Instalar la app.
2. Crear el restaurante.
3. Configurar áreas.
4. Crear un ingrediente llamado `Chicken Breast`.
5. Definir `lb` como unidad base.
6. Crear:

    * `oz = 0.0625 lb`
    * `case = 40 lb`
7. Registrar una compra de dos cajas.
8. Ver que se agregan 80 lb.
9. Realizar un conteo físico.
10. Registrar 3 lb de desperdicio.
11. Realizar un segundo conteo.
12. Ver el balance actual.
13. Ver el costo promedio.
14. Ver la diferencia entre conteos.
15. Exportar un CSV.
16. Crear un backup.
17. Desinstalar o limpiar datos de prueba.
18. Restaurar el backup.
19. Recuperar todos los registros correctamente.
20. Cerrar y abrir la aplicación sin perder datos.
21. Usarla sin internet.
22. Rotar el dispositivo sin perder formularios.
23. Utilizarla en teléfono y tablet.
24. Ejecutar pruebas y build sin errores.

---

# 35. Primer escenario obligatorio de prueba

Implementar como fixture automatizado:

```text
Ingredient: Chicken Breast
Base unit: lb

Unit options:
lb = 1 lb
oz = 0.0625 lb
case = 40 lb

Opening count:
20 lb

Purchase:
2 cases
80 lb total
$224 total
$2.80/lb

Waste:
3 lb

Final count:
65 lb
```

Resultado esperado:

```text
Initial inventory:      20 lb
Purchases:              80 lb
Waste:                   3 lb
Final inventory:        65 lb
Unclassified usage:     32 lb
```

El reporte debe producir exactamente ese resultado.

---

# 36. Documentación final

El README debe explicar:

* Qué hace la aplicación.
* Qué no hace.
* Arquitectura.
* Cómo compilar.
* Cómo ejecutar pruebas.
* Cómo crear datos demo.
* Cómo crear backup.
* Cómo restaurar.
* Cómo funcionan los movimientos.
* Cómo añadir una futura integración remota.

`docs/architecture.md` debe incluir:

```text
UI → ViewModel → Use Cases → Repositories → Room
```

`docs/database-schema.md` debe describir tablas y relaciones.

`docs/backup-format.md` debe documentar el ZIP y su versionado.

Crear también:

```text
docs/future-backend-migration.md
```

Debe explicar cómo añadir posteriormente:

* API.
* Sincronización.
* Multi-device.
* Web.
* Clover.

No implementar esas funciones ahora.

---

# 37. Resultado esperado del agente

Al terminar:

1. Resume las decisiones tomadas.
2. Lista los milestones completados.
3. Lista los archivos principales creados.
4. Reporta los comandos ejecutados.
5. Reporta el resultado de tests, lint y build.
6. Indica cualquier limitación real.
7. No afirmes que algo funciona si no fue compilado o probado.
8. No presentes únicamente snippets: el repositorio debe contener la implementación.
9. No añadas funcionalidades fuera de alcance sin justificarlo.
10. Prioriza corrección del modelo de inventario, estabilidad y facilidad de uso sobre animaciones o arquitectura excesiva.
