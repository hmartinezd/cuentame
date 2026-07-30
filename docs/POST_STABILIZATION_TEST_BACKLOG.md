# Post-Stabilization Test Backlog

Items to be addressed after backup creation is closed and restore work has begun.

## Reliability & Race Conditions
- [ ] Exhaustive ViewModel race condition testing for fast user interactions during backup creation.
- [ ] Systematic testing of every possible `OutputStream` failure stage (e.g., failure during ZIP finish vs entry write).

## UI & UX
- [ ] Exhaustive Settings backup UI permutations (different themes/locales combination with backup state).
- [ ] Additional report detail permutations (very large data sets, multiple currencies handling).

## Attachments
- [ ] Systematic testing of edge case attachment combinations (filenames with extreme characters, very large number of small files).
