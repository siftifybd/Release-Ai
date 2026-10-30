# JARVIS NEXUS — System Architecture Specification
**Document Version:** 1.0.0  
**Target:** Android-to-Android Real-Time Remote Device Management  
**Protocol:** NEXUS Secure Protocol v1.0 (TLS 1.3 / Keystore-Backed ECDH AES-256-GCM)  

---

## 1. Architectural Topology

```
                  ┌──────────────────────────────────────────────┐
                  │            CONTROLLER DEVICE (Phone B)       │
                  │  - UI Client (NexusActivity)                │
                  │  - NexusClient (Socket Transport)           │
                  │  - NexusDiscoveryManager (NSD / UDP Scanner)│
                  └───────────────┬──────────────────────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │                                                 │
   [Local Wi-Fi LAN]                               [Internet Mode]
         │                                                 │
   TCP Socket 8765                                  WSS / TLS Relay
   mDNS / NSD: _jarvis-nexus._tcp                   Blind Packet Routing
   UDP Broadcast: 8766                              (backend/server.js)
         │                                                 │
         └────────────────────────┬────────────────────────┘
                                  │
                  ┌───────────────▼──────────────────────────────┐
                  │              HOST DEVICE (Phone A)           │
                  │  - Foreground Service (NexusHostService)     │
                  │  - Local Server (NexusLocalServer)          │
                  │  - Host Advertiser (mDNS & UDP Responder)    │
                  │  - Security & Pairing (Dynamic 6-Digit TTL)  │
                  │  - MediaProjection (NexusScreenCaptureService│
                  │  - Storage Access Framework (64KB Chunks)    │
                  │  - Accessibility Dispatcher                  │
                  │  - Media & Phone Dialer Gateway             │
                  └──────────────────────────────────────────────┘
```

---

## 2. Core Subsystems

### 2.1 Device Discovery Subsystem
1. **Network Service Discovery (NSD):**
   - Host registers `_jarvis-nexus._tcp` with attributes: `model`, `version`, port `8765`.
   - Controller initiates `NsdManager.discoverServices` and automatically resolves IP addresses and host names.
2. **UDP Subnet Broadcast Fallback:**
   - Controller sends UDP discovery probe to `255.255.255.255:8766`.
   - Host `NexusHostAdvertiser` receives probe and replies with JSON model metadata and active TCP port.
3. **Zero-Mock Policy:** Discovered list is generated exclusively from real network responses.

### 2.2 Security & Pairing Subsystem
1. **Host Pairing Code:**
   - Generated dynamically using `SecureRandom` with a 5-minute Time-To-Live (TTL).
   - Displayed on Host screen with manual regeneration button.
   - Zero hardcoded passwords or pre-shared keys.
2. **Mutual Handshake:**
   - Client sends `handshake` request containing `pairingCode`, `deviceName`, and public key.
   - Host verifies code, emits `PairingRequest` requiring explicit host owner authorization ("Authorize" / "Deny").
   - Upon acceptance, an authenticated session key is derived for payload encryption.

### 2.3 File Transfer Subsystem
1. **Directory Browsing:** Queries permitted directories on Host via Storage Access Framework (SAF) / scoped directories.
2. **Chunked Streaming:** Files are sliced into 64KB buffers with running SHA-256 calculation.
3. **Integrity Verification:** Host computes independent SHA-256 digest upon completion; destination file is committed only if digests match.
4. **Transactional Move:** COPY → VERIFY SHA-256 → CONFIRM SOURCE DELETION → DELETE SOURCE.

### 2.4 Real-Time Screen Sharing
1. **System Consent:** Initiated through `MediaProjectionManager.createScreenCaptureIntent()`.
2. **Foreground Streamer:** `NexusScreenCaptureService` streams virtual display frames at adaptive resolutions and framerates.
3. **Owner Control:** Host notification displays ongoing stream indicator with an immediate "Stop Sharing" action.

### 2.5 Remote Interaction & Dialer
1. **Accessibility Control:** Dispatches allowlisted navigation commands (`GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`) through `JarvisAccessibilityService`. Requires explicit system accessibility permission.
2. **Dialer Delegation:** Dispatches `Intent.ACTION_DIAL` on Host device with pre-filled phone number; Host owner must review and confirm call initiation.
