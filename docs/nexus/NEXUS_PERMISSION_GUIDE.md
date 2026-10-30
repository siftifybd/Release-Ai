# JARVIS NEXUS — Permissions & OS Compliance Guide
**Document Version:** 1.0.0  
**Scope:** Android SDK 26 to 34 (Android 8.0 to Android 14)  

---

## 1. Android Manifest Permissions

| Permission | Protection Level | Purpose in JARVIS NEXUS |
|---|---|---|
| `INTERNET` | Normal | Socket communication for LAN TCP/UDP and Internet WSS relay |
| `ACCESS_NETWORK_STATE` | Normal | Checks network connectivity status and active transport (Wi-Fi vs Cellular) |
| `ACCESS_WIFI_STATE` | Normal | Reads local Wi-Fi IP address and network link properties |
| `CHANGE_WIFI_MULTICAST_STATE` | Normal | Enables mDNS / DNS-SD multicast packet reception for NSD discovery |
| `FOREGROUND_SERVICE` | Normal | Allows persistent background service execution for Host and Screen Capture |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Normal (SDK 34+) | Declares connected device management purpose for `NexusHostService` |
| `FOREGROUND_SERVICE_DATA_SYNC` | Normal (SDK 34+) | Declares chunked file synchronization purpose for transfers |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Normal (SDK 34+) | Required for `NexusScreenCaptureService` when sharing screen |
| `POST_NOTIFICATIONS` | Runtime (SDK 33+) | Displays persistent ongoing foreground service status and pairing requests |
| `WAKE_LOCK` | Normal | Prevents CPU sleep during active file transfers |
| `RECORD_AUDIO` | Runtime | Used by voice commands and audio streaming |
| `CAMERA` | Runtime | Used for QR code scanning and authorized camera preview |

---

## 2. Capability Matrix & Owner Authorizations

Every high-privilege remote feature is strictly gated behind device owner authorization:

1. **Host Foreground Availability (`NexusHostService`):**
   - Must be explicitly started by the Host user via the UI ("START" button).
   - Shows ongoing notification with IP/Port and "Stop Service" button.
2. **Device Pairing:**
   - Requires entry of dynamic 6-digit code with 5-minute validity.
   - Host presents dialog showing Controller name and IP; Host user must tap "Authorize".
3. **Screen Sharing:**
   - Host OS prompts user with system `MediaProjection` consent dialog ("Start Recording or Casting").
   - Can never be initiated silently in the background.
4. **Remote Interactions:**
   - Delegated to Android Accessibility Service (`JarvisAccessibilityService`).
   - If Accessibility is not enabled in Android System Settings, the app informs the user and disables the action.
5. **Phone Calls:**
   - Uses `Intent.ACTION_DIAL` (not silent `CALL_PHONE`).
   - Host device opens system dialer pre-populated with number; owner must confirm by pressing Call.
6. **File System & Storage:**
   - Restricted to Storage Access Framework (SAF) and scoped directories (`DIRECTORY_DOWNLOADS`, `DIRECTORY_DOCUMENTS`, `DIRECTORY_PICTURES`).
   - No unrestricted root or sensitive system directory exposure.
