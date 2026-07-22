# Onboarding and Setup

## Overview
The onboarding flow is designed to ensure a consistent local-first setup for new users. It uses a persistent draft strategy to prevent data loss during the configuration process.

## Flow Sequence
1.  **Welcome:** Introduction to the application and its local-first nature.
2.  **Restaurant Details:** Entry of restaurant name, base currency, and application language.
3.  **Inventory Areas:** Selection of suggested areas (e.g., Walk-in Cooler) and creation of custom areas. At least one area is required.
4.  **Ingredient Categories:** Selection/creation of categories to help organize items.
5.  **Review:** Final summary of configuration before committing to the database.

## Persistent Draft
Onboarding progress is saved automatically to Preferences DataStore as a JSON-serialized `OnboardingDraft`. 
*   **Auto-save:** Every meaningful change (step navigation, name entry, selection toggle) triggers a write.
*   **Resume:** If the application is closed or crashes, it re-opens at the last saved step with all previous inputs restored.
*   **Cleanup:** The draft is cleared only after successful database insertion and completion flag updates.

## Completion Integrity
The `CompleteOnboardingUseCase` orchestrates the final transition:
1.  **Atomic Transaction:** `LocalSetupRepository.completeSetup` inserts the restaurant, areas, and categories in a single Room transaction.
2.  **Recovery:** If a restaurant exists but setup is incomplete (no areas), it recovers by updating the existing restaurant and adding new areas/categories.
3.  **Idempotency:** The setup repository detects if a restaurant already exists with complete setup and returns `AlreadyCompleted`.
4.  **Crash Recovery:** `ResolveAppStartStateUseCase` checks both Room and DataStore. If Room is setup but DataStore is not, it repairs the DataStore flag.

## Startup Resolution
`AppStartState` drives the root UI:
*   `Loading`: Initial state while inspecting data sources.
*   `RequiresOnboarding`: Shown if no active restaurant or areas exist.
*   `Ready`: Shown when configuration is complete.
The transition is reactive; when setup completes, the root state automatically switches to `Ready`.

## Settings Management
After onboarding, users can manage their configuration in the Settings hub:
*   **Restaurant Profile:** Edit name and currency.
*   **Areas/Categories:** Add, rename, reorder, and archive.
*   **Appearance:** Toggle between Light/Dark/System themes and enable/disable Dynamic Color.
*   **Language:** Switch between English and Spanish.
