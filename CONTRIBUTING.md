# Contributing to Runout

Thank you for your interest in contributing to **Runout**! We welcome bug reports, feature suggestions, and code contributions that align with our core design philosophy.

---

## Core Design Principles

Before opening a pull request, please ensure your proposed changes adhere to our core rules:
1. **Zero Server Costs:** Runout has no centralized backend or database. Features must operate locally on-device or leverage free, open public protocols (e.g. Nostr, Internet Archive, Cover Art Archive).
2. **Audiophile Transparency:** Audio must remain lossless in and lossless out. Never resample downwards or alter bit depth unless explicitly commanded by the user (e.g. EQ).
3. **Tactile Vinyl Ritual:** Keep physical metaphors authentic: records have sides, tonearms follow geometry, platters have inertia.
4. **Rights-Safe Sharing:** Audio files are only packaged for original music and Creative Commons releases. All other albums travel as metadata cards.
5. **No Broad Storage Permissions:** Always maintain scoped access via Android's Storage Access Framework (SAF).

---

## Development Workflow

### 1. Code Style
- Runout is written in idiomatic Kotlin with Jetpack Compose.
- Code should be concise, readable, and well-documented.
- Maintain existing architecture boundaries: UI (`ui/`), Audio DSP (`sound/`), Networking (`net/`), Accounts (`account/`).

### 2. Testing Requirements
- Every new feature or bug fix must be covered by automated tests.
- Run the full test suite before submitting:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- All 12 test suites must pass cleanly.
- Verify zero Android Lint warnings:
  ```bash
  ./gradlew lintDebug
  ```

### 3. Commit Conventions
We follow clear, descriptive commit messages:
```text
feat(turntable): add anti-skate visual dial calibration
fix(tagreader): handle truncated ID3v2.4 COMM frames
docs(sound): clarify AutoEq parametric transfer function
test(dsp): add biquad impulse response benchmark
```

---

## Reporting Issues
- Use the provided [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md) or [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md).
- When reporting playback issues, include device model, Android version, audio file format (sample rate / bit depth), and output route (headphones, Bluetooth, USB DAC).
