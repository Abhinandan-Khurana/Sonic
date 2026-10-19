# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.3.x   | :white_check_mark: |
| < 0.3.0 | :x:                |

---

## Reporting a Vulnerability

Runout implements cryptographic signatures (BIP-340 Schnorr over secp256k1), ZIP file extraction (`.vinyl`), and WebSocket networking with public Nostr relays.

If you discover a security vulnerability (such as a zip-slip archive extraction issue, key leakage bug, or relay injection flaw):
1. **Do not open a public GitHub issue.**
2. Send an email to the repository owner or open a private GitHub security advisory.
3. Include detailed steps to reproduce the vulnerability and proof-of-concept payloads where applicable.

We will acknowledge receipt within 48 hours and work with you on a coordinated disclosure timeline.
