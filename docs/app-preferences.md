# Application Preferences

## Storage
UI preferences and onboarding drafts are stored in **Jetpack Preferences DataStore**.

## Preference Keys
*   `onboarding_completed` (Boolean): Flag for setup status.
*   `theme_mode` (String): `SYSTEM`, `LIGHT`, or `DARK`.
*   `dynamic_color_enabled` (Boolean): Material You preference.
*   `app_locale_tag` (String): BCP 47 tag.
*   `onboarding_draft` (String): Version 2 JSON.

## Authority
*   **Before Setup:** DataStore is the source of truth.
*   **After Setup:** `Restaurant.localeTag` in Room is authoritative. DataStore acts as a cache for the Android application-locale.
*   **Repair:** Startup and Settings observe Room to repair the DataStore locale cache if they diverge.

## Corruption Handling
The `loadOnboardingDraft()` operation synchronously removes invalid JSON or unsupported versions from DataStore to prevent repeat errors.

## Deterministic Persistence
The `OnboardingViewModel` uses an explicit `flushDraft()` before navigation to ensure the destination step is saved before the UI transitions.
