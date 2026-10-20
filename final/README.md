# Final — reviewed independent Java candidate

This folder contains an optimized, self-contained position/geometry utility for movable panels. It is **not** an integration or a claim of product readiness.

## Deliverables

- `src/main/java/floatingpanel/FloatingPanelPosition.java` — immutable normalized coordinate model, bounded parser, caller-supplied storage abstraction, safe-area placement and drag conversion.
- `src/test/java/floatingpanel/FloatingPanelPositionTest.java` — 12 JUnit regression cases, including randomized bounded geometry.
- `pom.xml` — optional Java 17 + JUnit 4.13.2 local test setup.

## Improvements from the first candidate

- Consolidated a verbose implementation into a smaller Java API, keeping the original public operations: `defaults`, `of`, `ofExplained`, `place`, `dragTo`, `toStorageValue`, `restore`, `persist` and `fromStorageValue`.
- Duplicate X/Y storage keys use a **first-occurrence-wins** policy and produce a diagnostic instead of overwriting a previously valid value.
- Maximum stored input length: **512 characters**; maximum delimited fields: **16**. Larger records safely fall back to defaults.
- Invalid data diagnostics do not echo arbitrary stored field content.
- Drag position subtraction uses `long` before clamping, guarding even extreme 32-bit screen origins.
- The storage contract is explicit; the library never writes outside a direct call to `persist`.

## Validation statement

These 12 tests are committed for later local execution via `mvn -B test`. **They have not been executed against this condensed repository version in the present GitHub session**; no remote test or workflow was triggered. This distinction is intentional.

The utility has no Android/window dependency. Actual display behavior, access control, gesture authorization, safe-region determination, persistence durability and caller integration remain outside its scope.

## Restrictions

Do not trigger GitHub Actions or add workflow files. Do not access or modify other repositories. The reviewed code is an independent candidate and no downstream application has been changed.
