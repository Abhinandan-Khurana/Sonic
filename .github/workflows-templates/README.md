# Workflow templates

These files are inactive until placed in `.github/workflows/`.

`ci.yml` verifies the pinned native core, builds all four Android ABIs, runs JVM tests and lint, and uploads build/test artifacts. Copy it to `.github/workflows/android.yml` using a GitHub login allowed to write workflows. The login used to publish this integration has repository write access but lacks the separate `workflow` scope, so this template is supplied without enabling it.

The previous automatic release template has been retired: it used outdated prerequisites and treated a debug-signed prototype as a normal release. Follow [the reviewed development release process](../../RELEASE.md). CI does not sign production updates or publish releases automatically.
