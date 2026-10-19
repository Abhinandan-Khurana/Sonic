> Historical v0.3 documentation: Soulseek replaces the online Nostr features in v0.4, and music is FLAC-only. See [current Soulseek behavior](SOULSEEK.md) and [build instructions](BUILDING.md).

# Decentralized Identity & Nostr Relay Architecture

One of Runout's core innovations is providing **cross-device user profiles, friend lists, and username search without operating any centralized servers**.

This is accomplished by leveraging the **Nostr** open protocol and public relay infrastructure.

---

## 1. Cryptographic Identity (No Passwords)

Instead of traditional email/password accounts stored in a central database, a Runout identity is a cryptographic keypair generated entirely on the user's Android device:
- **Private Key (`nsec`):** A cryptographically secure 256-bit scalar generated via `java.security.SecureRandom`. It never leaves the device.
- **Public Key (`npub`):** The corresponding coordinate $P = d \cdot G$ on the `secp256k1` elliptic curve.
- **Key Encoding:** Formatted as Bech32 strings (`nsec1...` and `npub1...`) conforming to **NIP-19**.

### Pure Kotlin BIP-340 Schnorr Implementation
Runout includes an in-tree, pure-Kotlin implementation of **BIP-340 Schnorr signatures** (`Schnorr.kt`):
- Does not require native C/Rust JNI libraries.
- Implements RFC 6979 deterministic nonce generation.
- Verified against every official Bitcoin BIP-340 test vector.
- Validates real Nostr events fetched from public relays during automated test suites.

---

## 2. Nostr Event Kind 33760: User Profile & Shelf

Runout utilizes Nostr **Kind 33760**, an addressable, parameterized replaceable event:
- Being "replaceable", relays store only the most recent published profile for a given public key, discarding historical updates.
- The event is signed with the user's private key; relays can store and relay it, but can never alter or spoof its contents.

### Event Schema Example
```json
{
  "id": "e4a2...b71c",
  "pubkey": "7f3a...91b2",
  "created_at": 1729350000,
  "kind": 33760,
  "tags": [
    ["d", "runout"],
    ["t", "runout"],
    ["n", "a"],
    ["n", "ab"],
    ["n", "abh"],
    ["n", "abhi"]
  ],
  "content": "{\"username\":\"abhi\",\"name\":\"Abhijit\",\"color\":\"#2B6BFF\",\"shelf\":[{\"title\":\"Paper Moons\",\"artist\":\"Ines Varga\",\"year\":2024,\"quality\":\"FLAC · 24-bit · 96 kHz\",\"color\":\"#7B61FF\"}]}",
  "sig": "38a1...59cf"
}
```

---

## 3. Serverless Prefix Search

Traditional username lookups require database `LIKE 'abhi%'` SQL queries. Without a database server, Runout solves this through **tag prefix indexing**:
1. When publishing a profile with username `abhi`, Runout automatically attaches `n` tags for every character prefix:
   - `["n", "a"]`, `["n", "ab"]`, `["n", "abh"]`, `["n", "abhi"]`
2. When a user searches for `"abh"`, Runout sends a standard Nostr REQ filter to the relays:
   ```json
   ["REQ", "sub_search", {
     "kinds": [33760],
     "#t": ["runout"],
     "#n": ["abh"]
   }]
   ```
3. Relays evaluate tag matches in constant time ($O(1)$) and return matching candidate profiles.
4. The client verifies the BIP-340 signature of every returned event, discarding any malformed or tampered records.

---

## 4. Collision-Resistant Handles: The 4-Character Code

Because there is no central authority to enforce unique usernames across the globe, two independent users can select the username `abhi`.

Runout resolves handle ambiguity by appending the first 4 hex characters of the user's public key:
- User 1: `@abhi·7F3A`
- User 2: `@abhi·C21B`

Friends can verify the 4-character code when exchanging handles or scanning in person.

---

## 5. Relay Mesh & Resilience

Runout connects concurrently to 3 established, high-availability public Nostr relays:
- `wss://relay.damus.io`
- `wss://nos.lol`
- `wss://relay.primal.net`

### WebSocket Lifecycle
- Runout does not maintain long-running background connections. Relays are contacted on-demand during active app usage (search, profile refresh, friend sync).
- OkHttp WebSocket clients query all 3 relays in parallel, de-duplicating event responses by their unique event ID.
