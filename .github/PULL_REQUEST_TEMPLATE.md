## Problem and resulting behavior

Describe the concrete problem and what a user will observe after this change.

## Validation

List relevant tests, manual checks, skipped checks and remaining limitations. Include Android/ABI coverage for JNI changes and migration coverage for database changes.

## Review notes

- [ ] Offline FLAC playback and both download providers' sign-in gates remain intact.
- [ ] No credentials, private messages, signing keys or local build paths are committed.
- [ ] Documentation, schema exports and native provenance/notices are updated where relevant.
- [ ] Relevant checks pass with zero lint errors; new warnings are addressed or explained.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for architecture and testing guidance.
