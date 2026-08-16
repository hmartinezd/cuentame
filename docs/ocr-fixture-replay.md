# OCR evidence fixture replay

Real-invoice parser fixtures contain the exact `OcrPageEvidence` emitted by ML Kit. They must not be edited to match an expected parse.

## Capture

1. Run a debug build and scan the invoice normally.
2. Open the raw OCR evidence screen after recognition.
3. Use the share action in the top bar. It exports one `page-N.json` file per persisted OCR page, before parser interpretation.
4. Copy the files without modification to `app/src/test/resources/ocr-fixtures/<fixture-name>/`.
5. Add `pages.txt` in that directory, listing the page filenames in invoice order.

The action is absent from release builds. Export stays on-device until the developer chooses a local share destination; it performs no upload, telemetry, LLM call, or supplier transformation.

## Replay and expectations

Load pages with `OcrEvidenceFixtureLoader.loadPages("<fixture-name>")`, pass the returned list directly to `DeterministicPurchaseInvoiceParser.parse`, and compare business fields with `assertGoldenInvoice`. Its failure report includes expected and parsed products, missing and unexpected products, field and total mismatches, and parser warnings.

When a golden fails, compare the source invoice with the JSON first:

- If JSON text differs from the invoice and the parser follows JSON, classify it as an OCR issue.
- If JSON is correct and the parser loses or misassigns it, classify it as a parser issue.
- If text or geometry supports multiple readings, classify it as ambiguous evidence.

Do not tune parser heuristics as part of fixture capture. Record the exact fixture, evidence, parsed field or row, and classification for a separate parser change.

## Current fixture inventory

`synthetic-loader-contract` exists only to test loading, page order, metadata preservation, and replay wiring. It is not real invoice evidence. No captured JC Foods or Restaurant Depot evidence was present when this workflow was added; both still require capture.
