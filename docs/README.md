# Documentation

Everything about Runout, grouped by who is looking for it. The [README](../README.md) is the
front door: what the app does, how to install it, and what changed in each release.

## Using Runout

| Document | What it covers |
| :--- | :--- |
| [README](../README.md) | The app, the features, and how to install the current release |
| [The handbook](HANDBOOK.md) | Installing, setting up, and the original v0.3 design history |
| [Pressing and Friday Drops](PRESSING-AND-DROPS.md) | Claiming weekly drops and the record-pressing model |
| [Record format](RECORD-FORMAT.md) | The `.vinyl` container: contents, checksums and import rules |
| [Sound and output](SOUND.md) | Pure, Hi-res, ReplayGain, the EQ and the output chain |
| [The turntable](TURNTABLE.md) | The deck, the platter and the player controls |
| [Soulseek](SOULSEEK.md) | The protocol, storage, credentials, messages and privacy |

## Building and developing

| Document | What it covers |
| :--- | :--- |
| [Building](BUILDING.md) | Toolchain, prerequisites, native builds, ABIs and page alignment |
| [Developer notes](DEVELOPMENT.md) | Where things live, how to run the checks, and local conventions |
| [Architecture](ARCHITECTURE.md) | The layers, the data flow and the boundaries between them |
| [Native bridge](../native/README.md) | The JNI adapter and the vendored `soulseek-rs` changes |
| [Contributing](../CONTRIBUTING.md) | Product boundaries and how to propose a change |
| [Release process](../RELEASE.md) | Building, verifying, tagging and publishing a release |
| [Changelog](../CHANGELOG.md) | Every notable change, per version |

## Testing

| Document | What it covers |
| :--- | :--- |
| [Argent flows](../.argent/README.md) | Saved Android flows for the manual UI checks |
| [CI workflow template](../.github/workflows-templates/README.md) | The inactive GitHub Actions workflow and how to enable it |

## Design and history

| Document | What it covers |
| :--- | :--- |
| [Design checklist](design/UI-REDESIGN.md) | The screen-by-screen design interview and the status of every change |
| [Agent handoff](AGENT-HANDOFF.md) | Project context for a coding agent picking the work up |

## Repository map

```text
app/      the Android application, its layers and its tests
native/   the Rust JNI bridge and the vendored Soulseek library
docs/     this documentation
scripts/  build and check helpers
release/  release notes, checksums and verification reports
.argent/  saved Android UI flows
.github/  issue and pull request templates, security policy
```

## Conventions

- Documentation is one file per subject, in the present tense, written for the person who will
  read it next.
- Behaviour changes come with a changelog entry and, where they can be observed, a test.
- The [handbook](HANDBOOK.md) and [pressing guide](PRESSING-AND-DROPS.md) began as v0.3 design
  history. Where they mention Nostr, multi-format audio or accounts, the current documentation
  above supersedes them.
