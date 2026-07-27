# Contributing to Cuentame

Thank you for contributing to Cuentame! To ensure the application remains reliable and well-documented for our users, please follow these guidelines.

## Documentation Maintenance Policy

Every change that modifies user-visible behavior must review and update the following, when applicable:

1.  **README.md**: Update the customer feature summary if capabilities change.
2.  **docs/USER_GUIDE.md**: Ensure the English guide reflects current workflows.
3.  **docs/USER_GUIDE.es.md**: Ensure the Spanish guide remains factually synchronized with the English version.
4.  **UI Strings**: Update English and Spanish resource files (`strings.xml`) to use consistent terminology.
5.  **Project Status**: Update the development/milestone status in README.

### Documentation Quality Rules

*   **Accuracy:** Describe only behavior that is present in the current code. Do not mark planned features as available.
*   **Clarity:** Use plain language and avoid technical jargon in customer-facing guides.
*   **Localization:** English and Spanish guides must remain factually identical. Avoid literal machine translations; use natural phrasing for each language.
*   **Currency/Dates:** Use customary localized formatting in documentation examples.

## Verification Requirements

All contributions must pass the full verification suite before being merged:

*   `./gradlew assembleDebug` (Compilation)
*   `./gradlew testDebugUnitTest` (Unit tests)
*   `./gradlew lintDebug` (Static analysis)
*   `./gradlew connectedDebugAndroidTest` (Instrumentation & E2E)

Ensure that new features include appropriate unit and integration tests.
