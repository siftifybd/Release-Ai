# JARVIS NEXUS — Wi‑Fi LAN + Internet Remote Control
**বাংলা Master Prompt | UI/UX | Architecture | Functions | Permissions | Security | Test Plan**

> এই ফাইলটি AI coding tool-কে existing JARVIS Android project এবং reference image-এর সঙ্গে দেবে। এটি implementation specification; নিজে থেকে working app বা backend নয়।

## 1. লক্ষ্য ও কীভাবে কাজ করবে

JARVIS NEXUS হবে JARVIS অ্যাপের একটি secure multi-device module।

- **LAN mode:** দুটি ফোন একই Wi‑Fi/LAN-এ থাকলে সরাসরি authenticated local connection।
- **Internet mode:** দুটি ফোন আলাদা network-এ থাকলেও secure backend/relay দিয়ে connection।
- **Unified mode:** একই device list, pairing, permission/capability model, command protocol ও audit log।
- App দেখাবে `LAN`, `Internet Relay`, `Connecting`, `Offline`—বাস্তব connection state অনুযায়ী।
- IP address নিজে authentication নয়। একই Wi‑Fi-তে থাকা মানেই device trusted নয়।
- Internet mode-এর জন্য backend/relay, domain ও TLS deployment লাগবে। ফোনের port public Internet-এ খুলে দেওয়া বা router port-forwarding default সমাধান হবে না।

### ব্যবহার করার নিয়ম
1. এই Markdown এবং reference image AI coding tool-এ attach করো।
2. Existing project ZIP/repository দাও।
3. AI-কে প্রথমে project audit করতে বলো।
4. UI → pairing → LAN → basic actions → Internet relay → optional advanced features—এই ধাপে implement করাও।
5. দুইটি বাস্তব Android ফোনে test না করে feature complete বলবে না।

---

# PART A — MASTER PROMPT (Copy/Paste)

তুমি একজন Senior Android Engineer, Backend Engineer, Network Security Engineer এবং UI/UX Designer। আমার existing Android app **JARVIS**-এর মধ্যে **JARVIS NEXUS** নামে secure Wi‑Fi LAN এবং Internet Remote Control system তৈরি করো।

## A1. Project audit — আগে, কোড পরিবর্তনের আগে

- Project structure, package name, Kotlin/Java, XML/Compose/WebView, Gradle ও target SDK পরীক্ষা করো।
- Existing navigation, theme, Room database, API client, authentication, Gemini integration, voice, camera, MediaProjection এবং permission handler খুঁজে বের করো।
- Existing components reuse করো; duplicate Activity, database, API client বা navigation graph তৈরি করবে না।
- Existing Home/Chat design নষ্ট করবে না।
- অপ্রয়োজনে dependency/Gradle version পরিবর্তন করবে না।
- Audit report-এ architecture, reusable files, নতুন files, backend needs, permission rationale ও blockers জানাও।

## A2. Two connection modes

### LAN mode
- Same Wi‑Fi/LAN-এ থাকা user-owned devices pair হবে।
- QR code অথবা short-lived pairing code দিয়ে pair।
- Local HTTPS/WSS বা equivalent authenticated TLS transport ব্যবহার করো।
- NSD/mDNS discovery optional; manual IP/port fallback থাকবে।
- Discovery authentication নয়।
- Wi‑Fi client isolation, guest network, VPN, DHCP IP change, IPv4/IPv6 ও port conflict handle করো।
- Internet বন্ধ থাকলেও LAN path available থাকলে local control চলবে।
- Local server চালু/বন্ধ করার দৃশ্যমান user control থাকবে।

### Internet mode
- Controller এবং target—দুই device-ই backend/relay-তে outbound authenticated TLS/WSS connection করবে।
- Backend account, device identity, pairing ও capability grant যাচাই করবে।
- Relay শুধু অনুমোদিত paired device-এর message route করবে।
- Router port-forwarding বা public inbound phone port প্রয়োজন হবে না।
- FCM optional wake-up hint হিসেবে ব্যবহার করা যায়; push payload-এ secret বা sensitive command রাখবে না।
- Target offline হলে স্পষ্ট Offline দেখাবে; fake success নয়।
- Relay unavailable হলে command failed/queued (শুধু safe, expiring queue থাকলে) হিসেবে দেখাবে।

## A3. UI screens

NEXUS dashboard:
- Header: `JARVIS NEXUS` / `Secure Device Network`
- Status card: Online / Offline / Connecting
- Transport badge: LAN / Internet Relay
- `Pair New Device`
- `Connect with IP`
- `My Devices`
- `Remote Permissions`
- `Activity & Security Logs`
- `Network Diagnostics`
- `Pause Remote Access`
- `Disconnect All`

Device card:
- User-selected device name/avatar
- Online/Offline এবং Last seen
- Actual route (LAN/Relay)
- Enabled capabilities summary
- Connect, Permissions, Rename, Revoke

Pairing screens:
1. Pair as Target: `Make this phone discoverable`, QR, short code, expiry countdown, Cancel/Regenerate।
2. Pair as Controller: QR scan বা code entry, target identity verify।
3. Permission review: requested capabilities আলাদা আলাদা Allow/Deny।
4. Success: paired device, granted capabilities, Open Device, Manage Permissions।

QR/code invitation single-use ও short-lived হবে। QR-এ permanent token/private key থাকবে না। 6-digit code একা যথেষ্ট নয়—expiry, rate limit, identity verification ও cryptographic proof লাগবে।

Connect with IP:
- IP address, port, pairing code, optional fingerprint confirmation।
- IPv4/IPv6 এবং port range validate।
- IP-কে authentication হিসেবে ধরবে না।
- Arbitrary network-wide scanning করবে না।
- Default port উদাহরণ `8765`, তবে conflict হলে actual port দেখাবে।

## A4. Remote capability system

প্রতিটি target device-এ capability default Off থাকবে। Target user নিজে enable করবে।

| Capability | Function |
|---|---|
| `device.status.read` | Basic JARVIS device status |
| `device.battery.read` | Battery/charging status |
| `network.status.read` | Network connection status |
| `jarvis.chat.open` | Target-এ JARVIS chat open |
| `jarvis.chat.send_prompt` | Target JARVIS-এ prompt পাঠানো |
| `media.play_pause` | Supported media session play/pause |
| `media.next` | Supported media session next |
| `file.send.request` | Target-কে selected file পাঠাতে request |
| `file.receive.accept` | User-approved incoming file accept |
| `clipboard.share` | User action-এর পর text share |
| `voice.session.request` | Target user-কে voice session শুরু করতে request |
| `screen.share.request` | MediaProjection consent চাওয়া |
| `camera.preview.request` | Target user-এর local approval-সহ camera |
| `notification.summary.read` | আলাদাভাবে অনুমোদিত notification summary |

Capability grant Android OS permission-এর বিকল্প নয়। Sensitive action-এর জন্য OS permission/system prompt এবং local approval আলাদা লাগবে।

**নিষিদ্ধ:** arbitrary shell/ADB execution, hidden camera/microphone, covert screen capture, silent file browsing, SMS/call log/contact/private app data access, lock-screen/PIN bypass, silent app install/uninstall, security protection disable, permission dialog bypass। Accessibility Service-কে hidden remote-control backdoor হিসেবে ব্যবহার করবে না।

## A5. Command protocol

প্রতিটি command-এ থাকবে:
- protocol version
- unique command ID
- sender device ID
- target device ID
- capability/action name
- issued-at এবং expiry
- nonce/replay-protection value
- authenticated payload
- optional response correlation ID

Target যাচাই করবে:
1. Sender authenticated ও paired কি না।
2. Device revoked কি না।
3. Target ID মেলে কি না।
4. Capability enabled কি না।
5. Command allowlist-এ আছে কি না।
6. Timestamp/expiry valid কি না।
7. Nonce/command ID replay হয়েছে কি না।
8. Sensitive action-এর local confirmation আছে কি না।

Unknown command reject করবে। কোনো string shell, SQL, JavaScript বা arbitrary Android Intent হিসেবে execute করবে না।

## A6. Encryption & identity

- সব network traffic TLS 1.2+; সম্ভব হলে TLS 1.3।
- Device private key Android Keystore-এ রাখবে।
- Short-lived access token এবং rotating refresh credential।
- Token/private key/pairing secret plain SharedPreferences বা logs-এ নয়।
- Pairing-এ cryptographically random one-time challenge, expiry, rate limit ও identity verification।
- Revoke করলে active session, token ও device authorization বাতিল।
- Relay operator থেকেও message content গোপন রাখতে হলে established protocol/library দিয়ে application-level end-to-end encryption design করো; নিজে cryptography invent করবে না।
- Key rotation, lost device recovery ও re-pairing flow define করো।

## A7. LAN transport implementation

- Authenticated TLS WebSocket/HTTPS ব্যবহার করো।
- Paired identity verify করো (public-key/certificate-based authentication)।
- NSD/mDNS discovery optional; discovery কখনো trust grant করবে না।
- Manual IP/port supported থাকবে।
- Network switch হলে controlled reconnect।
- Bounded retry/backoff; revoke বা disconnect-এর পর retry বন্ধ।
- Background socket অনির্দিষ্টকাল চালু রাখবে না।
- Local server state user-visible হবে।

## A8. Internet relay implementation

- Target এবং controller উভয়েই outbound WSS/TLS connection করবে।
- Backend device credentials ও account ownership verify করবে।
- Relay শুধু authorized device pair-এর encrypted/authorized message forward করবে।
- Pairing, login, command, file transfer rate-limit করবে।
- Device/account revoke, audit log ও abuse detection থাকবে।
- Backend secret Android APK-তে রাখা যাবে না।
- Backend deployment ছাড়া Internet remote mode complete দাবি করবে না।

## A9. File transfer

- Photo Picker/Storage Access Framework দিয়ে user-selected file।
- File name, MIME type, size ও destination preview।
- Target-এর accept action ছাড়া transfer নয়।
- Maximum size/type allowlist/quota/timeout।
- Encrypted transfer ও hash verification।
- Progress, pause/cancel, success/failure এবং partial-file cleanup।
- Broad storage permission শুধু convenience-এর জন্য নয়।

## A10. Screen share, camera, voice

- Screen share: target device-এ Android MediaProjection system consent flow; দৃশ্যমান sharing indicator ও Stop button।
- Camera: target user locally start/approve করবে; visible camera indicator।
- Voice: `RECORD_AUDIO` runtime permission; listening/stop/error state দৃশ্যমান।
- Remote request নিজে থেকে camera/mic/screen capture চালু করতে পারবে না।
- Permission revoked/session ended/app lifecycle অনুযায়ী capture বন্ধ করতে হবে।

## A11. Session/lifecycle states

`Idle`, `Discovering`, `Pairing`, `AwaitingApproval`, `Authenticating`, `ConnectingLan`, `ConnectingRelay`, `ConnectedLan`, `ConnectedRelay`, `Reconnecting`, `Offline`, `Revoked`, `Error`।

- Auth success ছাড়া Connected দেখাবে না।
- Network change-এ reconnect বা Offline।
- Disconnect-এ socket/service/resource release।
- Background connection প্রয়োজন হলে target SDK অনুযায়ী সঠিক foreground-service type, notification ও Android background limits অনুসরণ করবে।
- User-controlled Pause Remote Access এবং Disconnect All থাকবে।

## A12. Backend/API contract

সম্ভাব্য endpoint:
| Method | Path | কাজ |
|---|---|---|
| POST | `/v1/pairing/start` | Pairing শুরু |
| POST | `/v1/pairing/complete` | Authenticated pairing সম্পন্ন |
| POST | `/v1/devices/register` | Device public identity register |
| GET | `/v1/devices` | Paired device list |
| PATCH | `/v1/devices/{id}` | Rename/metadata |
| POST | `/v1/devices/{id}/revoke` | Device revoke |
| POST | `/v1/sessions` | Session authorize |
| DELETE | `/v1/sessions/{id}` | Session end |
| WS | `/v1/relay` | Authenticated relay |
| POST | `/v1/files/init` | File transfer start |
| POST | `/v1/files/complete` | Verify transfer |
| GET | `/v1/audit-events` | Security log |

প্রতিটি endpoint-এ auth, account ownership, paired-device authorization, validation, rate limit, expiry ও audit থাকতে হবে। Client-supplied device ID বিশ্বাস করবে না।

## A13. Data model

**NexusDevice:** `id`, `accountId`, `displayName`, `publicKey`, `platform`, `pairedAt`, `lastSeenAt`, `revokedAt`, `status`

**DeviceCapability:** `deviceId`, `capability`, `enabled`, `updatedAt`, `requiresLocalApproval`

**PairingRequest:** `id`, `initiatorDeviceId`, `targetDeviceId`, `challengeHash`, `expiresAt`, `usedAt`, `status`

**RemoteSession:** `id`, `controllerDeviceId`, `targetDeviceId`, `transport`, `startedAt`, `endedAt`, `status`

**AuditEvent:** `id`, `actorDeviceId`, `targetDeviceId`, `action`, `result`, `createdAt`, `redactedMetadata`

Raw pairing code completion-এর পর store করবে না। Existing Room থাকলে safe migration করবে; destructive migration দিয়ে JARVIS chat history মুছবে না।

## A14. Design tokens

| Element | Color |
|---|---|
| Background | `#FFFCF7` |
| Surface | `#FFFFFF` |
| Primary text | `#30203A` |
| Secondary text | `#8B7894` |
| Coral | `#FF805D` |
| Violet | `#8B5CF6` |
| Pink | `#E879B5` |
| Cyan | `#55C7E8` |
| Border | `#EBD9CE` |
| Success | `#20B889` |
| Error | `#D95364` |

Rounded card, subtle shadow/glow, clear status text, minimum 48dp touch target, TalkBack labels, responsive layout। Color alone দিয়ে Online/Offline বোঝাবে না।

## A15. Final deliverables

- Project audit report।
- NEXUS UI ও navigation।
- Secure pairing ও device registry।
- LAN transport।
- Internet backend/relay source বা integration contract।
- Per-device capability management।
- Authenticated allowlisted command protocol।
- Audit/revoke/disconnect system।
- Implemented optional file/screen/camera features।
- Unit/integration/two-device test results।
- Manifest permission changes ও rationale।
- Build result এবং known limitations।

Mock UI-কে working connection বলবে না। Backend deploy না হলে Internet mode complete বলবে না। দুইটি device test ছাড়া LAN complete বলবে না।

---

# PART B — DESIGN BLUEPRINT

## 2. Dashboard layout

```text
┌─────────────────────────────────────┐
│ ‹ Back       JARVIS NEXUS      ⚙    │
│          Secure Device Network      │
├─────────────────────────────────────┤
│ ● ONLINE                 LAN ACTIVE │
│ Local connection verified           │
├─────────────────────────────────────┤
│ [ + PAIR NEW DEVICE ]               │
│ [ Connect with IP ]                 │
├─────────────────────────────────────┤
│ MY DEVICES                          │
│ ┌─────────────────────────────────┐ │
│ │ ● My Android Phone              │ │
│ │   Connected • LAN               │ │
│ │   3 capabilities enabled        │ │
│ │   [Open] [Permissions] [⋮]      │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ ○ Tablet                        │ │
│ │   Offline • Last seen 2h ago    │ │
│ │   [Connect] [Permissions]       │ │
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ [Permissions] [Activity] [Network]  │
│ [Pause Remote Access]               │
└─────────────────────────────────────┘
```

Required screens:
1. NEXUS dashboard
2. Pair as Target (QR/code)
3. Pair as Controller (scan/enter code)
4. Enter IP/port
5. Verify device identity
6. Review capability request
7. Pairing success
8. Device details
9. Per-device permissions
10. Remote actions dashboard
11. File transfer preview/progress
12. Screen share consent/active state
13. Camera consent/active preview
14. Network diagnostics
15. Activity/security log
16. Revoke/Disconnect All confirmation

## 3. Interaction flow

### Pairing
```text
Target: Start pairing → create expiring challenge → show QR/code
Controller: Scan/enter → verify target identity
Target: Approve pairing request
Both: Establish authenticated session → save paired device
```

### LAN connect
```text
Choose LAN → discover or enter IP/port
→ TLS handshake + paired identity proof
→ validate capability → Connected (LAN)
```

### Internet connect
```text
Controller + Target → outbound WSS to relay
→ authenticate device credentials
→ backend verifies pairing/capability
→ relay routes encrypted message → Connected (Relay)
```

### Remote command
```text
Controller action → create signed/authenticated command
→ transport → target validates identity, expiry, nonce, capability
→ local approval if sensitive → allowlisted handler
→ result + audit event → controller UI
```

### Revoke
```text
Owner taps Revoke → confirm
→ backend/local registry revokes device
→ terminate active session → invalidate credentials
→ update device list and audit log
```

---

# PART C — ANDROID PERMISSIONS

## 4. Permission matrix

| Permission/API | Purpose | Notes |
|---|---|---|
| `INTERNET` | LAN sockets and backend traffic | Normal permission; no runtime dialog |
| `ACCESS_NETWORK_STATE` | Detect connectivity | Normal permission |
| `ACCESS_WIFI_STATE` | Some Wi‑Fi connection info | Android-version restrictions apply |
| `CHANGE_WIFI_MULTICAST_STATE` | Multicast discovery when required | Only if implementation needs it |
| `NEARBY_WIFI_DEVICES` | Certain nearby Wi‑Fi APIs on Android 13+ | Only for applicable APIs; use `neverForLocation` only when valid |
| `RECORD_AUDIO` | Voice feature | Runtime permission |
| `CAMERA` | In-app camera | Runtime permission; camera intent may avoid it |
| `POST_NOTIFICATIONS` | Android 13+ notifications | Runtime permission |
| `FOREGROUND_SERVICE` | Foreground service | If used |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Connected-device FGS type | Only if service genuinely qualifies for this type |
| `FOREGROUND_SERVICE_DATA_SYNC` | Data-sync FGS type | Not a generic permanent socket workaround |
| `VIBRATE` | Haptic feedback | Normal permission |

Important:
- Local TCP/UDP socket-এর জন্য আলাদা “Wi‑Fi control” permission লাগে না।
- `INTERNET` normal permission, runtime prompt নয়।
- Wi‑Fi discovery/info API-এর Android-version-specific restrictions যাচাই করবে; প্রয়োজন ছাড়া location permission চাইবে না।
- Photo Picker/SAF ব্যবহার করো; `MANAGE_EXTERNAL_STORAGE` এড়িয়ে চলো।
- MediaProjection-এর system consent লাগে; manifest permission একা screen capture দেয় না।
- Notification Listener access user-কে Android Settings থেকে আলাদাভাবে enable করতে হয়; `POST_NOTIFICATIONS` তার বিকল্প নয়।
- Basic remote messaging-এর জন্য Accessibility permission দরকার নেই।
- Background persistent connection Android background limits-এর অধীন; সঠিক service type target SDK ও use case অনুযায়ী নির্ধারণ করতে হবে।
- অপ্রয়োজনীয় SMS, contacts, call logs, broad storage বা background location permission যোগ করবে না।

---

# PART D — SECURITY CHECKLIST

## 5. Must-have security controls

- [ ] Pairing challenge random, single-use, short-lived।
- [ ] Pairing attempts rate-limited।
- [ ] QR-এ permanent secret নেই।
- [ ] Both devices verify intended peer identity।
- [ ] Private key Android Keystore-এ।
- [ ] All network traffic TLS।
- [ ] Relay account ownership ও pairing verify করে।
- [ ] Capability default Off এবং per-device।
- [ ] Sensitive actions local confirmation চায়।
- [ ] Command expiry ও replay protection আছে।
- [ ] Access token short-lived; refresh rotation।
- [ ] Revoke active sessions/credentials বন্ধ করে।
- [ ] Logs-এ token/key/code/private content redact।
- [ ] File size/type limit ও hash verification।
- [ ] Disconnect All সব session/local server বন্ধ করে।
- [ ] Arbitrary shell, hidden capture বা permission bypass নেই।

Threat tests: guessed/expired/reused pairing code, stolen device, revoked device, replay command, client isolation, network switch, spoofed device ID, expired token, oversized file, app process kill, denied camera/mic/screen consent।

---

# PART E — IMPLEMENTATION ROADMAP

## 6. Phases

**Phase 1 — UI prototype:** Dashboard, pairing, device cards, permission screen, logs, diagnostics। Mock status স্পষ্টভাবে mock থাকবে।

**Phase 2 — Secure pairing:** Device key identity, QR/manual code, expiry, local approval, registry, revoke।

**Phase 3 — LAN:** Authenticated TLS local transport, manual IP/port, optional NSD/mDNS, reconnect, two-phone tests।

**Phase 4 — Basic controls:** Battery/status, JARVIS chat open/send, disconnect, capability settings, audit log।

**Phase 5 — Internet relay:** Backend deploy, WSS relay, account/device authentication, routing, offline status, token rotation, revocation, monitoring।

**Phase 6 — Advanced features:** Approved file transfer, clipboard share, media session, MediaProjection screen share, camera preview, voice session।

**Phase 7 — Release hardening:** Security review, background/battery tests, privacy review, signed release build, backend monitoring।

## 7. Test matrix

| Test | Expected |
|---|---|
| Same Wi‑Fi, Internet off | LAN works after pairing |
| Different networks, Internet on | Relay works after pairing |
| Relay offline | Offline; no false success |
| Guest Wi‑Fi/client isolation | Clear LAN error |
| Wrong/expired/reused code | Reject |
| Revoked device | Session terminated |
| Capability disabled | Command rejected |
| Camera/screen consent denied | Capture does not start |
| Network changes mid-session | Reconnect or Offline |
| App process killed | Safe recovery; no phantom session |
| Invalid/large file | Reject safely |
| Replayed command | Reject |
| Disconnect All | All sessions stop |

## 8. Build commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
```

Backend deployment needs HTTPS domain, valid TLS certificate, secret manager/environment variables, database, WSS relay, backups, monitoring, rate limits and privacy/data-retention rules. Backend secrets APK-তে রাখবে না।

---

# 9. Developer-কে শেষ নির্দেশনা

> এই Markdown এবং attached reference image অনুসরণ করে আমার existing JARVIS Android project-এ JARVIS NEXUS implement করো। প্রথমে project audit করো। Existing architecture বজায় রেখে UI, secure pairing, LAN transport, basic allowlisted actions, তারপর Internet backend/relay আলাদা phase-এ তৈরি করো। প্রতিটি remote action authenticated, permission-scoped, auditable এবং target user-controlled হবে। Hidden camera/mic/screen capture, arbitrary shell, lock bypass বা private data access তৈরি করবে না। Backend deploy ও দুইটি আলাদা network-এ test না করে Internet mode complete বলবে না। Changed files, permission rationale, backend setup, test results ও limitations জানাবে।

## 10. বাস্তব সীমাবদ্ধতা

- Same Wi‑Fi থাকলেও router client isolation/firewall peer connection আটকাতে পারে।
- Internet remote শুধু IP address দিয়ে নিরাপদে করা যায় না; backend/relay, domain ও TLS লাগে।
- Android sandbox ও OS permission বজায় থাকবে; সব ফোনের সম্পূর্ণ system control সম্ভব নয়।
- প্রথমে status, chat, connect/disconnect, capability permissions ও logs বানাও; পরে file transfer/screen/camera যোগ করো।
- Security ছাড়া remote control release করবে না।
