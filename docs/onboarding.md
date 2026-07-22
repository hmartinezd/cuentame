# Onboarding and Setup

## Overview
The onboarding flow ensures a consistent local-first setup. It uses a hardened persistence and validation layer to prevent data loss and corruption.

## Persistent Draft
Onboarding progress is saved as a JSON-serialized `OnboardingDraft` (Version 2).
*   **Sequential Autosave:** A `Mutex` ensures only one write executes at a time.
*   **Debounce:** Name typing is debounced by 300ms.
*   **Flush:** State is flushed synchronously before every navigation or completion step.
*   **Cleanup:** Invalid or unsupported draft versions are removed synchronously during loading.

## Unified Ordering
Suggested and custom items are combined into a single reorderable list.
*   Stable IDs (Template Keys or UUIDs) ensure identity survives draft restoration.
*   Sort orders are normalized to a contiguous zero-indexed sequence during persistence.

## Completion Integrity
The `CompleteOnboardingUseCase` manages the transition:
1.  **Validation:** `LocalSetupValidator` checks all fields before submission.
2.  **Room First:** Restaurant and items are committed in one Room transaction.
3.  **Repair:** `ResolveAppStartStateUseCase` reactively observes Room. If setup is complete but DataStore is out of sync (e.g., locale mismatch or missing completion flag), it repairs DataStore from the authoritative Room record.

## Setup Recovery
If a user has a restaurant record but no areas (incomplete setup), the onboarding flow reuses the existing restaurant ID and completes the setup instead of creating duplicates.

## Settings Management
Users can edit configuration in the Settings hub. 
*   **Restaurant:** Updates are committed to Room first, then DataStore.
*   **Areas:** Archiving is prevented if it's the last active area for that restaurant.
*   **Validation:** The same business rules are enforced in Settings as in Onboarding.
