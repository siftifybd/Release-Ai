# Implementation Plan - JARVIS NEXUS Wi-Fi Control & Firebase Host Service

This plan outlines the complete implementation of Wi-Fi management permissions, a runtime permission check flow, accurate Host Service ON/OFF status telemetry, and real-time host discovery via Firebase Firestore and local Network Service Discovery (NSD).

## User Review Required

> [!IMPORTANT]
> - **Permissions Requested**: `CHANGE_WIFI_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `NEARBY_WIFI_DEVICES` (Android 13+).
> - **Service & Discovery**: Host Service ON/OFF toggle will control `NexusHostService`, update local notification status, and broadcast real-time telemetry to Firebase Firestore.
> - **Search & Connect**: Remote device search will use real dual-mode discovery (NSD mDNS local broadcast + Firestore cloud registry) with zero fake data.

---

## Proposed Key Changes

### 1. `AndroidManifest.xml`
- Declare `android.permission.NEARBY_WIFI_DEVICES` for Android 13+ (API 33+) alongside existing `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION`, and `ACCESS_COARSE_LOCATION`.

### 2. `NetworkControlManager.kt`
- Add permission verification methods: `hasWifiPermissions(context)` and `hasLocationPermissions(context)`.
- Enhance `setWifiEnabled()` and `toggleWifi()` to check runtime permissions before attempting state changes, gracefully falling back to launching system Wi-Fi panels when restricted.

### 3. `NexusActivity.kt`
- Add `ActivityResultLauncher<Array<String>>` for runtime permission requests (`ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES`, `POST_NOTIFICATIONS`).
- Add prominent **Wi-Fi & Location Permission Card** in `NexusActivity` when permissions are missing, offering 1-tap request dialogs.
- Bind Host Service ON/OFF switch dynamically to `NexusHostService.isRunning`, updating server status text, IP, port, and online badge in real time.
- Implement real search & connect handler in the discovery pane, querying live Firestore host documents and local mDNS services.

### 4. `FirebaseNetworkRepo.kt` & `NexusHostService.kt`
- Ensure `markOffline()` is called on service stop or app destroy so Firestore accurately reflects device availability.
- Ensure host heartbeat updates `lastSeenIso` and active network status every time connectivity changes.

---

## Verification Plan

### Automated Verification
1. Run `compile_applet` to verify zero build or syntax errors.
2. Run `gradle :app:testDebugUnitTest` to verify unit test suite.

### Functional Verification
- Verify Host Service ON/OFF toggle starts/stops `NexusHostService` and updates status badges in real time.
- Verify runtime permission request dialog triggers when tapping Wi-Fi controls if location/nearby permissions are missing.
