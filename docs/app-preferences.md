# Application Preferences

## Storage
Application-wide preferences and onboarding progress are stored using **Jetpack Preferences DataStore**.

## Preference Keys
*   `onboarding_completed` (Boolean): Flag indicating if setup was finished.
*   `theme_mode` (String): `SYSTEM`, `LIGHT`, or `DARK`.
*   `dynamic_color_enabled` (Boolean): Android 12+ dynamic color preference.
*   `app_locale_tag` (String): BCP 47 language tag (e.g., `en-US`, `es-US`).
*   `onboarding_draft` (String): JSON-serialized draft state.

## Default Values
*   Onboarding Completed: `false`
*   Theme: `SYSTEM`
*   Dynamic Color: `true`
*   Locale: `en-US`

## Data Ownership
| Property | Source of Truth | Cached in DataStore |
| :--- | :--- | :--- |
| Restaurant Name | Room | No |
| Currency Code | Room | No |
| App Language | Room (`localeTag`) | Yes (`app_locale_tag`) |
| Areas/Categories | Room | No |
| Theme/Dynamic Color | DataStore | N/A |
| Onboarding Progress | DataStore | N/A |

## Synchronization and Repair
The `ResolveAppStartStateUseCase` is responsible for keeping Room and DataStore in sync regarding the `onboarding_completed` flag and application language.
*   If Room has valid setup but DataStore says incomplete, DataStore is updated to `true`.
*   If Room is empty but DataStore says complete, DataStore is reset to `false` to force onboarding.

## Corruption Handling
The `DataStoreAppPreferencesRepository` handles decoding errors gracefully. If the onboarding draft JSON is corrupted, it is logged and treated as `null`, allowing the user to start over rather than crashing the app.
