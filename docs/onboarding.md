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

## Unified Ordering
Suggested and custom items are combined into a single ordered list during onboarding.
*   **Item Model:** Each item has an ID (template key or UUID), optional template reference, custom name, and a sort order.
*   **Reordering:** Users can move any selected item (suggested or custom) up or down the list.
*   **Persistence:** The order is preserved in the draft and committed to Room as contiguous sort orders starting at zero.

## Sequential Autosave
The flow uses a deterministic sequential autosave pipeline:
*   **Debounce:** Text entry (restaurant name) is debounced by 300ms to avoid excessive writes.
*   **Immediate Save:** Selections, reordering, and step changes are saved immediately.
*   **Sequential Writes:** A `Mutex` ensures only one DataStore write operation executes at a time, preventing older states from overwriting newer ones.
*   **Flush:** State is explicitly flushed to DataStore before navigation actions (Next, Back, Finish) to ensure no progress is lost.

## Completion Integrity
The `CompleteOnboardingUseCase` orchestrates the final transition:
1.  **Authoritative Validation:** `LocalSetupValidator` enforces all business rules (names, currency, locale, areas, sort orders) before any data is committed.
2.  **Atomic Transaction:** `LocalSetupRepository.completeSetup` inserts the restaurant, areas, and categories in a single Room transaction.
3.  **Recovery:** If Room setup succeeds but DataStore completion fails, the application repairs the state upon next startup by observing the Room setup and updating DataStore/Locale accordingly.
4.  **Idempotency:** Setup cannot be overwritten once completed.

## Corruption and Versioning
*   **Supported Version:** Version 2.
*   **Handling:** If an unsupported version or corrupted JSON is detected in DataStore, the invalid entry is automatically removed, and the user starts with a fresh setup.


## Settings Management
After onboarding, users can manage their configuration in the Settings hub:
*   **Restaurant Profile:** Edit name and currency.
*   **Areas/Categories:** Add, rename, reorder, and archive.
*   **Appearance:** Toggle between Light/Dark/System themes and enable/disable Dynamic Color.
*   **Language:** Switch between English and Spanish.
