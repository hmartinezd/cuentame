# Post-Stabilization Test Backlog

| Test class | Current verified behavior | Deferred behavior | Why nonblocking | Milestone |
| :--- | :--- | :--- | :--- | :--- |
| `DetailedReportsUiTest` | Navigation to reports, seeded value check | Full detail visibility for all metric types | Core reports navigation and primary spend metrics are verified. | M8 |
| `ReportingRefreshComposeTest`| Navigation to reports | Automatic refresh on database mutation verification | Manual navigation/reload reflects current state; data integrity is safe. | M8 |
| `SettingsBackupUiTest` | Button existence and navigation | Progress bar and success/error message visibility | Repository orchestration and adversarial validation suites cover the core logic. | M8 |
| `WasteLifecycleTest` | Navigation and draft persistence | Complete interactive lifecycle (select, enter, save, post, void) | Repository-level lifecycle tests cover the state machine and data integrity. | M8 |
