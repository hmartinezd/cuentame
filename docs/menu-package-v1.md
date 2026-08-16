# MenuPackage V1

MenuPackage V1 is Cuentame's offline, commercial menu contract for a Sales Terminal. It is a deterministic projection of one immutable `MenuPublicationSnapshot`; it is never assembled from the current mutable menu catalog.

The JSON envelope uses `format: "cuentame-menu-package"` and `formatVersion: 1`. Readers must reject other formats or versions, unknown fields, invalid field types, invalid enum values, and packages that fail semantic validation.

## Identity and fields

- `packageId` is exactly the Cuentame `MenuPublicationId`.
- `restaurantId` identifies the owning restaurant.
- `menu.menuId` is the publication's source `MenuId`.
- `categoryId` is the source `MenuCategoryId` captured by the publication.
- `sellableItemId` is exactly the stable Cuentame `MenuRecipeId`.
- `commercialRevision` and `consumptionRevision` are the revisions captured when the menu was published. They are identities for later sales reconciliation; exporting does not increment them.

The package contains display names, prices, presentation order, the menu default cash-discount percentage, and each item's `APPLY_DEFAULT` or `NONE` behavior. `APPLY_DEFAULT` applies the menu's default percentage; `NONE` opts the item out.

MenuPackage contains no recipe, ingredient, unit, inventory, costing, supplier, or internal publication-row data.

## Canonical representation

Commercial decimals are JSON strings in plain notation, with trailing zeros removed and every numeric zero represented as `"0"`. `publishedAt` is the immutable publication time encoded as a UTC ISO-8601 `Instant`; there is no export timestamp.

Categories are ordered by `sortOrder`, then `categoryId`. Items within a category are ordered by `sortOrder`, then `sellableItemId`. The same publication therefore produces identical UTF-8 JSON bytes on every export.

Empty categories are valid, but the complete menu must contain at least one item. IDs and names must be nonblank, prices and revisions non-negative, the publication revision positive, currency valid, and the default discount in `[0, 100)`.

Future mutable menu edits cannot affect an existing package. A new publication revision is required to produce changed package content.
