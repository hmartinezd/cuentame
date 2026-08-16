# SalesExport V1

SalesExport V1 is the offline machine contract through which a Sales Terminal reports historical sales facts to Cuentame. Its JSON envelope is identified by `format: "cuentame-sales-export"` and `formatVersion: 1`.

## Identity and MenuPackage relationship

- `exportId` is the stable identity of the exported artifact.
- `terminalId` identifies the terminal installation and `restaurantId` its restaurant.
- `menuPackageId` is the exact `MenuPackage.packageId` used for the sales and therefore the Cuentame `MenuPublicationId`.
- `menuId` and `publicationRevision` are the values captured by that package.
- `transactionId` and `saleLineId` are stable Terminal identities. Both must be unique within an export; line identity is globally unique across its transactions.
- `sellableItemId` is the stable Cuentame `MenuRecipeId` and may occur on any number of lines.

## Historical values and decimals

SalesExport records historical facts. Cuentame must not substitute current MenuRecipe names, prices, or revisions for the snapshots in an export.

`quantity`, `unitPrice`, `gross`, `discount`, and `net` are decimal strings, never floating-point JSON numbers. Values are compared mathematically with decimal arithmetic: `gross = quantity × unitPrice`, `net = gross − discount`, and `discount <= gross`.

## Transactions

V1 supports only `COMPLETED` and `VOIDED`. A void changes transaction state; it does not erase the historical line snapshots or require their amounts to be zero. `businessDate` is an explicit Terminal-supplied business concept and is not inferred from timestamps.

The codec preserves transaction and line order exactly and adds no timestamps during encoding. Readers reject malformed JSON, unknown fields, incorrect field types, invalid values, duplicate transaction or line identities, and unreconciled arithmetic.

This contract does not perform local restaurant, publication, menu, or item lookups. Durable import persistence, duplicate-file handling, and inventory posting are separate milestones.
