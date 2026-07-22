# Database Migrations

## Overview

Cuentame uses Room for database management. As the schema evolves, we must provide explicit migration paths to preserve user data.

## Process for Schema Updates

When a schema change is required (e.g., adding a new table or column):

1.  **Update Entities:** Modify the `@Entity` classes.
2.  **Increment Version:** Increase the `version` number in `RestaurantInventoryDatabase.kt`.
3.  **Define Migration:** Create a `Migration` object defining the SQL changes.
    ```kotlin
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE ingredients ADD COLUMN sku TEXT")
        }
    }
    ```
4.  **Add to Database:** Register the migration in the `DatabaseModule.kt` (or where the database is built).
5.  **Export Schema:** Run `./gradlew kspDebugKotlin` to generate the new schema JSON in the `schemas/` directory.
6.  **Test Migration:** Add a test using `MigrationTestHelper` to verify that data is preserved and the schema is valid after migration.

## Migration Testing

We use the `androidx.room.testing.MigrationTestHelper` to automate migration verification.

Example test:
```kotlin
@Test
fun migrate1To2() {
    helper.createDatabase(DB_NAME, 1).apply {
        // Insert data using SQL
        close()
    }

    helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)
}
```

## Rules

*   **No Destructive Migrations:** Never use `fallbackToDestructiveMigration()`.
*   **Idempotent Seeding:** Ensure that seed data (like units) is handled correctly during migrations if applicable.
*   **Commit Schemas:** Always commit the generated JSON schemas in the `schemas/` folder.
