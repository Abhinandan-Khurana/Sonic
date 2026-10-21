# Security

Runout 0.4 is a development integration. Sensitive boundaries include the Soulseek JNI adapter and peer protocol, Android document access, credential storage, message persistence and `.vinyl` extraction. Historical v0.3 Nostr code is retained but no longer connected by the production graph.

Never attach passwords, private messages, account files or signing keys to a public issue. For a vulnerability, use the repository's private vulnerability reporting feature if enabled, or ask the maintainer for a private contact channel without publishing an exploit or private data.

Please include the affected source revision, Android version, minimal reproduction and expected impact. The development build uses Soulseek's plaintext protocol; Android Keystore protects optional credentials at rest, not network traffic. See [Soulseek behavior](../docs/SOULSEEK.md) for details.
