# JARVIS Android Assistant --- সম্পূর্ণ Future Features Implementation Plan

## বাংলা প্রযুক্তিগত গাইড \| Menu Hub, Android Permissions, Kotlin, Manifest, API, Database ও Test Plan

**প্রজেক্টের লক্ষ্য:** বিদ্যমান JARVIS Android অ্যাপের মধ্যে পুরোনো ও নতুন
ফিচারগুলো একটি কেন্দ্রীয় Menu Button / Feature Hub থেকে চালানো, প্রতিটি
ফিচারের আলাদা স্ক্রিন ও সেটিংস রাখা, এবং Android-এর নিরাপত্তা ও ব্যাকগ্রাউন্ড
নীতিমালা মেনে বাস্তবায়ন করা।

> **গুরুত্বপূর্ণ:** এই নথি হলো implementation plan ও integration blueprint;
> এটি নিজে থেকে কোড পরিবর্তন বা APK তৈরি করে না। প্রজেক্টের সঠিক package
> name, Gradle versions, minSdk/targetSdk, বিদ্যমান class ও resource
> names দেখে code snippets সামঞ্জস্য করতে হবে। উদাহরণে
> `com.jarvis.assistant` package দেখানো হয়েছে---প্রকৃত প্রজেক্টে ভিন্ন হলে
> পরিবর্তন করো।

------------------------------------------------------------------------

## 1. বর্তমান প্রজেক্টের সঙ্গে সামঞ্জস্য

প্রজেক্ট পর্যালোচনার আগের নোট অনুযায়ী অ্যাপে/প্রজেক্টে নিম্নলিখিত উপাদান রয়েছে
বা শুরু করা হয়েছে:

-   Kotlin Android application এবং XML/WebView-ভিত্তিক UI।
-   Gemini Live API integration।
-   Vosk speech recognition; বর্তমান model English হতে পারে, তাই বাংলা
    model ও ভাষা-নির্বাচন যাচাই করতে হবে।
-   Room database memory।
-   Voice enrollment-এর প্রাথমিক কাঠামো।
-   Orb/HUD UI।
-   Accessibility automation ও MediaProjection-এর কিছু কাঠামো।
-   YouTube এবং WhatsApp workflow।
-   `AppPreferences.kt`-এ API key সাধারণ SharedPreferences-এ রাখার ঝুঁকি।
-   Room migration-এ destructive fallback থাকার সম্ভাবনা।
-   WebView file access, exported components এবং custom endpoint-এর
    ব্যবহার যাচাই করা দরকার।
-   Gradle wrapper/build environment আগে download failure দিয়েছিল; তাই
    feature implementation-এর আগে clean build baseline তৈরি করতে হবে।

এগুলোকে চূড়ান্ত সত্য ধরে না নিয়ে repository-তে সংশ্লিষ্ট ফাইল খুঁজে যাচাই করতে
হবে। Existing class names আলাদা হলে নতুন duplicate class বানানোর বদলে
বিদ্যমান class-এ refactor করা হবে।

### প্রথমে সংগ্রহ/যাচাই করো

1.  `settings.gradle` / `settings.gradle.kts`
2.  Root এবং app-level `build.gradle` / `build.gradle.kts`
3.  `gradle/wrapper/gradle-wrapper.properties`
4.  `app/src/main/AndroidManifest.xml`
5.  `app/src/main/java/...` বা `app/src/main/kotlin/...`
6.  `app/src/main/res/layout/`, `menu/`, `navigation/`, `drawable/`
7.  Room `@Entity`, `@Dao`, `@Database` classes এবং migrations
8.  Voice service, API client, preferences, existing menu/navigation
    code
9.  `minSdk`, `targetSdk`, Kotlin, AGP, Gradle এবং JDK versions
10. বর্তমান build command-এর সম্পূর্ণ error log

------------------------------------------------------------------------

## 2. মূল UX সিদ্ধান্ত: একটি কেন্দ্রীয় Menu Button

### 2.1 Menu Button-এর আচরণ

-   Main screen-এ স্থায়ী, সহজে খুঁজে পাওয়া যায় এমন Menu button থাকবে।
-   Button চাপলে navigation drawer, side panel, অথবা grid-ভিত্তিক Feature
    Hub খুলবে।
-   ছোট স্ক্রিনে scrollable menu এবং বড় স্ক্রিনে grid ব্যবহার করা যাবে।
-   Menu-তে feature category, search, favorite/pinned items, permission
    status এবং settings থাকবে।
-   প্রতিটি item চাপলে সংশ্লিষ্ট feature screen বা workflow খুলবে।
-   Feature disabled বা permission অনুপস্থিত হলে "Permission
    required"/"Setup needed" দেখাবে; নীরবে ব্যর্থ হবে না।
-   Back button প্রথমে menu বন্ধ করবে, তারপর feature screen থেকে main
    screen-এ ফিরবে।
-   Feature screen-এর title, status, help এবং settings একই design
    pattern অনুসরণ করবে।

### 2.2 প্রস্তাবিত Menu structure

``` text
JARVIS
├── Home / AI Orb
├── Voice & Wake Word
│   ├── Language: বাংলা / English
│   ├── Wake Word settings
│   ├── Offline commands
│   └── Microphone / service status
├── Phone Control
│   ├── App launcher
│   ├── Flashlight / volume / settings shortcuts
│   ├── Device status
│   └── Lock phone / Android unlock screen
├── AI Chat & Memory
│   ├── Smart Chat
│   ├── Saved memory
│   ├── Conversation history
│   └── API / model settings
├── Automation
│   ├── My workflows
│   ├── Create workflow
│   ├── Approval queue
│   └── Run history
├── HUD & Appearance
│   ├── Orb / animation
│   ├── Themes
│   ├── Voice / speech speed
│   └── Accessibility-friendly mode
├── Security & Privacy
│   ├── Permission dashboard
│   ├── Device Admin status
│   ├── Notification access status
│   ├── Accessibility status
│   ├── Data export / delete
│   └── Action log
├── Notification Assistant
├── Camera / OCR / PDF
├── Reminder & Planner
├── Network & Device Diagnostics
├── Web Research & Translation
├── Backup / Restore / Export
└── Settings / About / Help
```

### 2.3 Menu architecture

-   `FeatureId`: প্রতিটি feature-এর stable identifier।
-   `FeatureCatalog`: feature label, icon, category, required setup ও
    destination-এর তালিকা।
-   `FeatureHubScreen`/`FeatureHubActivity`: menu UI।
-   `FeatureRouter`: নির্বাচিত feature-কে সংশ্লিষ্ট screen-এ route করবে।
-   `PermissionStatusRepository`: permission/access status পরীক্ষা করবে।
-   `FeatureSettingsRepository`: enable/disable, favorites, language,
    theme ইত্যাদি রাখবে।
-   Existing navigation system থাকলে সেটিই ব্যবহার করো; duplicate
    navigation framework যোগ কোরো না।

Menu items hard-code করে বহু Activity-তে ছড়িয়ে না দিয়ে একটি catalog থেকে
তৈরি করা উত্তম। তবে Android permission request বা special settings
access-এর জন্য feature-specific screen/handler আলাদা থাকবে।

------------------------------------------------------------------------

## 3. Feature Catalog --- ১২টি প্রধান ফিচার

  --------------------------------------------------------------------------------
  ID                Feature           প্রধান কাজ         প্রাথমিক নির্ভরতা
  ----------------- ----------------- ----------------- --------------------------
  `voice`           Advanced Voice &  বাংলা/ইংরেজি      Microphone, Speech engine,
                    Wake Word         voice, offline    foreground service
                                      command, wake     
                                      word              

  `phone`           Phone Control &   app launch,       Intents, Device Admin
                    Lock              supported         
                                      controls, lock,   
                                      real keyguard     

  `memory`          AI Memory & Smart Gemini chat,      API client, Room, secure
                    Chat              user-approved     key handling
                                      memory            

  `automation`      Multi-step        workflow,         Command parser, action
                    Automation        confirmation,     registry
                                      action execution  

  `hud`             Iron-Man Style    Orb, voice wave,  UI, lifecycle-aware
                    HUD               state animations  animation

  `security`        Security &        permission        Permission checks, audit
                    Privacy Center    dashboard, logs,  log
                                      secure settings   

  `notifications`   Notification      read              Notification Listener
                    Assistant         notifications,    access
                                      draft replies     

  `documents`       Camera / OCR /    capture/select,   Camera or Photo Picker,
                    PDF               OCR, document     OCR/PDF library
                                      summary           

  `planner`         Reminder & Daily  todo, routines,   Room,
                    Planner           scheduled         WorkManager/AlarmManager
                                      notifications     as appropriate

  `diagnostics`     Network & Device  network, battery, System APIs, network state
                    Diagnostics       storage, errors   

  `research`        Web Research &    web search,       Approved
                    Translation       sourced           search/translation API
                                      summaries,        
                                      translation       

  `backup`          Backup / Restore  user-selected     Storage Access Framework,
                    / Export          data              encryption
                                      export/import     
  --------------------------------------------------------------------------------

------------------------------------------------------------------------

## 4. Android Permissions --- প্রয়োজন অনুযায়ী চাইবে

### 4.1 Manifest permission matrix

  -----------------------------------------------------------------------------------------------------------------------------------------------
  Permission / Access                     ব্যবহার                        ধরন / সতর্কতা
  --------------------------------------- ----------------------------- -------------------------------------------------------------------------
  `INTERNET`                              Gemini, search, translation,  Manifest permission; runtime dialog নয়
                                          online OCR                    

  `ACCESS_NETWORK_STATE`                  network status                Manifest permission

  `RECORD_AUDIO`                          voice command                 Runtime permission; background mic-এর Android rules মানতে হবে

  `FOREGROUND_SERVICE`                    foreground services           Android version ও service type অনুযায়ী

  `FOREGROUND_SERVICE_MICROPHONE`         microphone foreground service সংশ্লিষ্ট Android version-এ প্রযোজ্য; while-in-use restrictions আছে

  `POST_NOTIFICATIONS`                    Android 13+ notifications     Runtime permission; foreground service notification requirements আলাদা
                                                                        করে মানতে হবে

  `WAKE_LOCK`                             নির্দিষ্ট কাজের সময় CPU জাগিয়ে  ঐচ্ছিক; স্ক্রিন অন করার নিশ্চয়তা দেয় না
                                          রাখা                          

  `VIBRATE`                               haptic feedback               ঐচ্ছিক

  `CAMERA`                                অ্যাপের নিজস্ব camera capture   শুধু camera feature দরকার হলে; Photo Picker দিয়ে import করলে দরকার নাও হতে
                                                                        পারে

  `READ_CONTACTS`                         contacts lookup               contacts feature থাকলে এবং runtime permission সহ

  `CALL_PHONE`                            সরাসরি কল শুরু                  সাধারণ dialer intent ব্যবহার করলে সাধারণত এড়ানো যায়

  `SCHEDULE_EXACT_ALARM`                  exact alarm                   কেবল সত্যিই exact timing দরকার হলে; special access/OS policy যাচাই করতে
                                                                        হবে

  `RECEIVE_BOOT_COMPLETED`                reboot-এর পর নির্বাচিত routine শুধু প্রয়োজন হলে; সীমিত কাজ ও নীতি মেনে
                                          পুনরায় schedule                

  `USE_BIOMETRIC`                         app-owned biometric prompt    Android system lock bypass করে না

  `BIND_DEVICE_ADMIN`                     DeviceAdminReceiver-এ system  `<receiver android:permission="android.permission.BIND_DEVICE_ADMIN">`;
                                          binding protection            user runtime permission নয়

  `BIND_NOTIFICATION_LISTENER_SERVICE`    NotificationListenerService   service declaration-এ permission; user Settings থেকে Notification Access
                                          binding                       চালু করবেন

  `BIND_ACCESSIBILITY_SERVICE`            AccessibilityService binding  শুধু প্রকৃত accessibility/use-case প্রয়োজন হলে; user Settings থেকে enable
                                                                        করবেন

  `SYSTEM_ALERT_WINDOW`                   overlay/floating window       বিশেষ access; প্রথমে in-app orb ব্যবহার করা ভালো

  `FOREGROUND_SERVICE_MEDIA_PROJECTION`   screen capture foreground     target Android version অনুযায়ী প্রযোজ্য
                                          service                       

  `POST_NOTIFICATIONS`                    routine/foreground            feature ও OS version অনুযায়ী
                                          notifications                 
  -----------------------------------------------------------------------------------------------------------------------------------------------

**এড়িয়ে চলবে:** `MANAGE_EXTERNAL_STORAGE`, root access, broad
SMS/call-log permissions, অপ্রয়োজনীয় Accessibility, অপ্রয়োজনীয় overlay
permission। Storage Access Framework (`ACTION_OPEN_DOCUMENT`,
`ACTION_CREATE_DOCUMENT`) বা Android Photo Picker দিয়ে কাজ হলে broad
storage permission যোগ কোরো না।

### 4.2 Manifest snippet --- template only

এটি পুরো Manifest নয়। বিদ্যমান Manifest-এর package, service declarations,
exported components এবং SDK rules দেখে merge করতে হবে।

``` xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Only if needed by the implemented feature -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <!-- Add CAMERA only if using in-app camera capture. -->

    <application
        android:allowBackup="false"
        android:usesCleartextTraffic="false">

        <!-- Keep existing application/activity/service declarations.
             Do not paste duplicate <application> or manifest nodes. -->

    </application>
</manifest>
```

`android:allowBackup="false"` একটি নিরাপদ প্রাথমিক বিকল্প, কিন্তু app data
backup/restore requirement অনুযায়ী explicit backup rules এবং Android
version behavior যাচাই করতে হবে। User-initiated encrypted export feature
আলাদাভাবে তৈরি করা যায়।

### 4.3 Device Admin receiver template

``` xml
<receiver
    android:name=".security.JarvisDeviceAdminReceiver"
    android:label="JARVIS Device Admin"
    android:description="@string/device_admin_description"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_receiver" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

`res/xml/device_admin_receiver.xml`:

``` xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

এটি lock করার জন্য Device Admin activation flow যোগ করে। ব্যবহারকারী নিজে
অনুমোদন করবেন। সাধারণ Device Admin ব্যবহার করে ফোনের PIN/Pattern জানা,
বাইপাস করা বা নিজে থেকে screen unlock করা যায় না।

### 4.4 Foreground microphone service notes

-   Android 12+ এ background থেকে foreground service start সীমাবদ্ধ।
-   Android 14+ এ foreground service type ও সংশ্লিষ্ট permission/manifest
    declarations আরও গুরুত্বপূর্ণ।
-   Microphone permission while-in-use হওয়ায় app background-এ থাকলে
    microphone service শুরু করার চেষ্টা ব্যর্থ হতে পারে।
-   App-কে foreground-এ এনে user action-এর মাধ্যমে service শুরু করা,
    persistent notification দেখানো এবং clear Stop control দেওয়া উত্তম।
-   Always-on hotword experience-এর জন্য Android-এর built-in hotword APIs
    বা platform privileges লাগতে পারে; সাধারণ third-party app সব
    পরিস্থিতিতে system-level hotword service পায় না।
-   Wake Word engine-এর CPU/battery consumption মাপতে হবে। Vosk সাধারণ
    speech recognition-এর বিকল্প হতে পারে, কিন্তু dedicated low-power
    wake-word detector-এর সমান নয়।

------------------------------------------------------------------------

## 5. Kotlin package ও module পরিকল্পনা

প্রকৃত package structure-এর সঙ্গে মেলাও; একই class দুবার তৈরি করবে না।

``` text
com.jarvis.assistant
├── app/
│   ├── JarvisApplication.kt
│   └── AppContainer.kt
├── navigation/
│   ├── FeatureId.kt
│   ├── FeatureCatalog.kt
│   └── FeatureRouter.kt
├── voice/
│   ├── VoiceController.kt
│   ├── WakeWordController.kt
│   ├── SpeechRecognizerAdapter.kt
│   ├── OfflineCommandParser.kt
│   └── VoiceForegroundService.kt
├── phone/
│   ├── PhoneActionController.kt
│   ├── DeviceLockController.kt
│   └── KeyguardPromptController.kt
├── ai/
│   ├── GeminiClient.kt
│   ├── ChatRepository.kt
│   └── PromptBuilder.kt
├── memory/
│   ├── MemoryEntity.kt
│   ├── MemoryDao.kt
│   └── MemoryRepository.kt
├── automation/
│   ├── WorkflowEntity.kt
│   ├── WorkflowStepEntity.kt
│   ├── WorkflowRunner.kt
│   ├── ActionRegistry.kt
│   └── ConfirmationPolicy.kt
├── security/
│   ├── PermissionStatusRepository.kt
│   ├── JarvisDeviceAdminReceiver.kt
│   ├── JarvisNotificationListener.kt
│   └── AuditLogRepository.kt
├── documents/
│   ├── DocumentImportController.kt
│   ├── OcrRepository.kt
│   └── PdfTextRepository.kt
├── planner/
│   ├── ReminderEntity.kt
│   ├── ReminderDao.kt
│   └── ReminderScheduler.kt
├── diagnostics/
│   └── DeviceDiagnosticsRepository.kt
├── research/
│   ├── SearchRepository.kt
│   └── TranslationRepository.kt
├── backup/
│   ├── ExportController.kt
│   └── ImportController.kt
└── data/
    ├── JarvisDatabase.kt
    ├── preferences/
    └── migrations/
```

এটি প্রস্তাবিত package map; existing codebase-এ equivalent class থাকলে
সেটিকে reuse/refactor করতে হবে।

------------------------------------------------------------------------

## 6. Menu Feature Model --- উদাহরণ

``` kotlin
enum class FeatureId {
    VOICE, PHONE, MEMORY, AUTOMATION, HUD, SECURITY,
    NOTIFICATIONS, DOCUMENTS, PLANNER, DIAGNOSTICS,
    RESEARCH, BACKUP
}

data class FeatureItem(
    val id: FeatureId,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val requiresSetup: Boolean = false
)
```

একটি catalog রাখো:

``` kotlin
object FeatureCatalog {
    val items = listOf(
        FeatureItem(FeatureId.VOICE, "Voice & Wake Word",
            "বাংলা, English ও offline commands", "mic", true),
        FeatureItem(FeatureId.PHONE, "Phone Control",
            "অনুমোদিত phone actions ও lock", "smartphone", true),
        FeatureItem(FeatureId.MEMORY, "AI Chat & Memory",
            "Gemini chat ও saved memory", "brain", true),
        FeatureItem(FeatureId.AUTOMATION, "Automation",
            "Multi-step workflows", "workflow", true),
        FeatureItem(FeatureId.HUD, "HUD Appearance",
            "Orb, animation ও themes", "orbit"),
        FeatureItem(FeatureId.SECURITY, "Security & Privacy",
            "Permissions ও action history", "shield-check"),
        FeatureItem(FeatureId.NOTIFICATIONS, "Notification Assistant",
            "নোটিফিকেশন পড়া ও draft reply", "bell", true),
        FeatureItem(FeatureId.DOCUMENTS, "Camera / OCR / PDF",
            "ছবি ও ডকুমেন্ট বিশ্লেষণ", "file-text", true),
        FeatureItem(FeatureId.PLANNER, "Reminder & Planner",
            "To-do ও routines", "calendar-clock"),
        FeatureItem(FeatureId.DIAGNOSTICS, "Device Diagnostics",
            "Network, battery ও storage", "activity"),
        FeatureItem(FeatureId.RESEARCH, "Web & Translation",
            "সোর্সসহ search ও অনুবাদ", "globe", true),
        FeatureItem(FeatureId.BACKUP, "Backup / Export",
            "নির্বাচিত data export/import", "database")
    )
}
```

`FeatureRouter` UI framework অনুযায়ী Activity, Fragment, Navigation
Component বা Compose route-এ পাঠাবে। যদি বর্তমান অ্যাপ WebView UI ব্যবহার
করে, menu catalog থেকে native screen ও WebView page-এর মধ্যে পরিষ্কার
boundary রাখো। WebView JavaScript bridge-কে সব Android action-এর
unrestricted gateway বানাবে না।

------------------------------------------------------------------------

## 7. Feature implementation details

### 7.1 Advanced Voice & Wake Word

**কাজ** - বাংলা/English language selector। - Speech-to-text result থেকে
intent parse। - Offline whitelist commands: "টর্চ অন", "ভলিউম কমাও",
"JARVIS বন্ধ", "অ্যালার্ম সেটিংস খোলো"। - Listening / Thinking / Speaking /
Error state। - Wake word আলাদা engine/adapter হিসেবে রাখা।

**Implementation** 1. `VoiceController` state machine তৈরি: `IDLE`,
`LISTENING`, `PROCESSING`, `SPEAKING`, `ERROR`. 2.
`SpeechRecognizerAdapter` বা Vosk wrapper-এর interface তৈরি। 3. Vosk
model language ও license/size যাচাই করে Bengali model download/ship
policy নির্ধারণ। 4. Offline command parser-এর জন্য normalization: Bengali
numerals, punctuation, common synonyms। 5. Unknown command হলে action না
চালিয়ে clarification চাইবে। 6. microphone permission এবং foreground
service state UI-তে দেখাবে। 7. service বন্ধ করার সহজ button ও
notification action থাকবে।

**সতর্কতা:** Android-এর সাধারণ SpeechRecognizer/Vosk app-কে সব সময়
background-এ microphone চালু রাখার নিশ্চয়তা দেয় না। Battery optimization,
Android version ও vendor policy অনুযায়ী পরীক্ষা দরকার।

### 7.2 Phone Control & Lock System

**সম্ভব** - `Intent` দিয়ে app/settings screen খোলা। - Supported APIs দিয়ে
flashlight বা app-owned settings control। -
`DevicePolicyManager.lockNow()`---Device Admin সক্রিয় থাকলে। -
`KeyguardManager`/system credential flow ব্যবহার করে user-কে আসল Android
lock screen-এ নিয়ে যাওয়া।

**অসম্ভব/করা হবে না** - PIN/Pattern/password নিজে পড়া বা সংরক্ষণ। -
Android lock screen bypass বা background থেকে গোপনে unlock। - অনুমতি ছাড়া
অন্য অ্যাপের secure UI-তে অননুমোদিত automation।

**Implementation** 1. Device Admin activation explanation screen। 2.
`DeviceAdminReceiver` + `res/xml/device_admin_receiver.xml`। 3.
Activation intent: `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`। 4.
`DeviceLockController` active admin status পরীক্ষা করবে। 5. Lock
command-এর আগে user preference/confirmation policy প্রয়োগ। 6. Unlock
command-এ system Keyguard prompt/normal device unlock flow; unlock সফল
হয়েছে কি না যাচাই করার জন্য privacy-respecting UI state ব্যবহার। 7.
OEM/Android version অনুযায়ী physical device test।

### 7.3 AI Memory & Gemini Smart Chat

**Architecture** - `GeminiClient`: network/API layer। -
`ChatRepository`: messages, context window ও errors। -
`MemoryRepository`: user-approved facts ও preferences। -
`PromptBuilder`: short context, safety rules ও user preferences।

**API security** - APK-তে long-lived Gemini secret key hard-code করা বা
plaintext preferences-এ রাখা উচিত নয়। - Production-এ নিজের
backend/proxy-তে secret রাখো; server-side quotas, rate limiting, auth,
abuse prevention যোগ করো। - Developer/test build-এ key দিলে
`local.properties`/CI secret ব্যবহার করো; Git-এ commit করবে না। -
TLS/HTTPS ব্যবহার; cleartext HTTP এড়াও। - API error, timeout, quota এবং
offline state সামলাও।

**Memory rules** - user-approved memory আলাদা করে store। - user-কে
memory দেখতে, edit করতে ও delete করতে দাও। - raw microphone
recordings/transcripts অনির্দিষ্টকাল ধরে store কোরো না। - sensitive data
default-এ memory-তে save কোরো না। - migration-এ
`fallbackToDestructiveMigration()` দিয়ে production data মুছে ফেলার বদলে
explicit Room migrations তৈরি করো।

### 7.4 Advanced Automation

Workflow-কে declarative, allowlisted step হিসেবে সংরক্ষণ করো।

Example:

``` json
{
  "name": "Good Morning",
  "steps": [
    {"type": "SPEAK_TIME"},
    {"type": "READ_TODAY_REMINDERS"},
    {"type": "OPEN_APP", "package": "user-selected-package"}
  ],
  "requiresConfirmation": false
}
```

-   JSON উদাহরণটি schema illustration; arbitrary code বা shell commands
    চালানোর জন্য নয়।
-   `ActionRegistry`-তে শুধু supported action types থাকবে।
-   Workflow validation-এর সময় step count, timeout ও required permission
    যাচাই।
-   `SEND_MESSAGE`, `MAKE_CALL`, `DELETE_DATA`, `PURCHASE`,
    `CHANGE_SECURITY`-এর মতো high-impact action-এ confirmation
    বাধ্যতামূলক।
-   AI-generated action plan কখনও সরাসরি execute না করে allowlist ও
    validation pass করাতে হবে।
-   Workflow cancel/pause/resume এবং run history রাখো।
-   Accessibility ব্যবহার করলে কেবল user-visible, consented,
    policy-compliant workflow-এর জন্য।

### 7.5 Iron-Man Style HUD UI

**Screen states** - Idle: Orb শান্তভাবে animate। - Listening: voice wave
/ ring। - Processing: spinner / pulsing effect। - Speaking: waveform। -
Error: clear message এবং retry action। - Permission missing:
feature-specific setup button।

**Implementation** - Existing XML UI হলে `View`, `ConstraintLayout`,
`RecyclerView`/`GridLayout` ও custom drawable/Canvas animation ব্যবহার
করা যায়। - WebView UI হলে animations CSS/JS-এ রাখা যায়, কিন্তু native
permission requests এবং secure Android actions native Kotlin layer-এ
থাকবে। - `Lifecycle`-এর সঙ্গে animation bind করো; screen background হলে
animation pause। - Reduce motion / low-power mode এবং contrast-friendly
theme রাখো। - HUD যেন button labels ও accessibility semantics আড়াল না
করে।

### 7.6 Security & Privacy Center

একটি dashboard-এ দেখাবে: - Microphone runtime permission status। -
Notification Access status। - Accessibility service enabled status (যদি
feature প্রয়োজন করে)। - Device Admin status। - Camera permission
status। - API connectivity status---কখনও secret key প্রকাশ করবে না। -
Memory/export/delete controls। - Clear audit log।

`PermissionStatusRepository` permission ও special access status আলাদা
করে check করবে। প্রতিটি special access-এর জন্য উপযুক্ত `Settings` intent
ব্যবহার করো; user-কে Settings-এ নিয়ে যাও, জোর করে toggle কোরো না।

**Audit log**-এ রাখো: action type, time, success/failure, user
confirmation, error category। Message body, PIN, API secret, full
notification content বা private document text ডিফল্টে log কোরো না।

### 7.7 Notification Assistant

-   `NotificationListenerService` এবং
    `BIND_NOTIFICATION_LISTENER_SERVICE` declaration।
-   Settings থেকে Notification Access user নিজে চালু করবেন।
-   User-selected apps allowlist।
-   Sensitive app/notification category exclude করার controls।
-   TTS-এ notification পড়ার আগে lock state ও headphone/private output
    policy বিবেচনা।
-   Draft reply প্রথমে UI-তে দেখাবে; auto-send নয়।
-   Reply action কেবল notification-এ app-provided `RemoteInput` action
    থাকলে, supported ও user-confirmed workflow দিয়ে।
-   Notification content database-এ স্থায়ীভাবে store না করাই ভালো; দরকার
    হলে explicit opt-in ও retention period।

### 7.8 Camera / OCR / PDF Assistant

**Import** - ছবি/ডকুমেন্ট বাছতে Android Photo Picker এবং Storage Access
Framework অগ্রাধিকার। - অ্যাপের নিজস্ব camera capture দরকার হলে তবেই
`CAMERA` runtime permission। - PDF থেকে text extraction-এর জন্য
Android/PDF library নির্বাচন করার আগে license, Android compatibility,
memory use যাচাই। - OCR-এর জন্য on-device ML Kit/Tesseract/অন্যান্য
compatible engine মূল্যায়ন; Bengali OCR support ও offline capability আলাদা
করে test।

**Privacy** - Document cloud API-তে পাঠানোর আগে confirmation। - Selected
file-এর temporary URI permission ও stream ব্যবহার। - বড় PDF page-by-page
process; পুরো ফাইল memory-তে load কোরো না। - OCR result-কে user-verified
text হিসেবে দেখাও; ভুল পড়তে পারে। - Temporary files cleanup ও user delete
option রাখো।

### 7.9 Reminder & Daily Planner

**Database** - Reminder ID, title, note (optional), due time, recurrence
rule, enabled flag, created/updated timestamp। - `Room`-এ source of
truth; scheduler কেবল next execution schedule করবে। - Exact timing না
লাগলে WorkManager বা inexact alarm। - সত্যিই exact alarm দরকার হলে
Android version/policy অনুযায়ী `SCHEDULE_EXACT_ALARM` special access
প্রয়োজন কি না যাচাই। - Android 13+ notification permission না থাকলে
reminder UI-তে warning দেখাও। - Reboot-এর পর schedule পুনরায় তৈরি করতে
হলে minimal `BOOT_COMPLETED` receiver বিবেচনা করো; প্রয়োজন নেই এমন কাজের
জন্য receiver যোগ কোরো না। - Time zone/time changes ও daylight saving
behavior test করো।

### 7.10 Network & Device Diagnostics

**Read-only diagnostics** - `ConnectivityManager` দিয়ে network
availability। - `BatteryManager`/battery intent থেকে battery status। -
`StatFs` বা উপযুক্ত API দিয়ে app-visible storage status। - App version,
Android version, model, SDK ও recent error category। - Android অন্য
অ্যাপের RAM/CPU usage পুরোপুরি প্রকাশ নাও করতে পারে; অপ্রাপ্য তথ্যের জন্য ভুয়া
"full device monitor" দেখাবে না।

Diagnostic report export করার আগে personal data বাদ দিয়ে user
confirmation নাও।

### 7.11 Web Research & Translation

-   Search API/approved web service-এর মাধ্যমে ফলাফল আনো।
-   Gemini-এর generated answer ও source-derived facts আলাদা করো।
-   প্রতিটি source-এর title, URL, retrieved time এবং summary রাখো।
-   Untrusted web page-কে instruction হিসেবে গ্রহণ কোরো না; page content
    যেন assistant-কে private data পাঠাতে বা Android action চালাতে নির্দেশ
    দিতে না পারে।
-   Translation language selector, copy/share, source text preview।
-   API quota, timeout, empty results, language mismatch এবং offline
    fallback test করো।

### 7.12 Backup / Restore / Export

-   User picks export destination with `ACTION_CREATE_DOCUMENT`; import
    with `ACTION_OPEN_DOCUMENT`।
-   Export options: selected memory, preferences, reminders, workflows,
    action logs---প্রতিটির checkbox।
-   API keys, tokens, passwords, raw recordings এবং notification body
    default export-এ থাকবে না।
-   Backup file-এ schema version, app version, export timestamp ও
    checksum।
-   Private data থাকলে encryption ব্যবহার; passphrase/key management স্পষ্ট
    করো।
-   Restore-এর আগে preview, conflict resolution, duplicate handling ও
    confirmation।
-   Database overwrite করার আগে backup snapshot; restore ব্যর্থ হলে
    rollback।
-   Import JSON হলে schema validation, size limit ও unexpected fields
    rejection।
-   Backup file-কে trusted executable content হিসেবে ব্যবহার কোরো না।

------------------------------------------------------------------------

## 8. Room Database design

বিদ্যমান database-এ table যোগ করার আগে current schema ও migration history
দেখো। নীচের তালিকা conceptual; সব table প্রথম release-এ প্রয়োজন নেই।

### 8.1 Suggested entities

1.  `ConversationEntity`: conversation ID, title, createdAt, updatedAt।
2.  `ChatMessageEntity`: message ID, conversation ID, role, content,
    timestamp, model metadata।
3.  `MemoryEntity`: ID, key/category, value, userApproved, createdAt,
    updatedAt।
4.  `WorkflowEntity`: ID, name, enabled, requiresConfirmation,
    createdAt, updatedAt।
5.  `WorkflowStepEntity`: ID, workflowId, position, actionType,
    validatedConfig।
6.  `WorkflowRunEntity`: ID, workflowId, start/end time, status, error
    category।
7.  `ReminderEntity`: ID, title, dueAt, recurrence, enabled,
    notification state।
8.  `AuditEventEntity`: ID, actionType, timestamp, result, confirmation
    flag, safe metadata।
9.  `AppPreferenceEntity` বা DataStore preferences: selected language,
    theme, wake-word preference, feature flags।

### 8.2 Database rules

-   Foreign keys ও indices যেখানে প্রয়োজন।
-   Room migration tests।
-   Sensitive content minimize; encryption requirement threat model
    অনুযায়ী।
-   `fallbackToDestructiveMigration()` production path-এ এড়াও।
-   API key Room/SharedPreferences-এ plain text হিসেবে রাখো না।
-   Database delete/export action-এর আগে confirmation।
-   Audit log retention সীমিত ও configurable রাখো।

------------------------------------------------------------------------

## 9. Preferences & secure settings

-   Non-sensitive settings-এর জন্য Jetpack DataStore বিবেচনা করো।
-   Secret key client app-এ স্থায়ীভাবে রাখা এড়িয়ে backend ব্যবহার করো।
-   যদি local credential/token রাখা অপরিহার্য হয়, Android Keystore-backed
    encryption ও key lifecycle review করো; Keystore নিজে plaintext API
    key hard-code করার সমস্যার সমাধান নয়।
-   Feature enable/disable, language, theme, TTS speed, memory opt-in,
    notification allowlist, data retention ও analytics consent-এর পৃথক
    settings।
-   Feature disabled হলে background work cancel/stop হবে।
-   Permission revoke হলে graceful fallback ও clear UI দেখাতে হবে।

------------------------------------------------------------------------

## 10. Suggested implementation phases

### Phase 0 --- Baseline & safety

-   Project ZIP backup/version control।
-   Build environment, Gradle/JDK/AGP এবং SDK versions যাচাই।
-   Existing manifest, menu, navigation, Room schema, services ও API
    client map।
-   `minSdk`/`targetSdk` ঠিক করা; build reproducible করা।
-   Plaintext API key, WebView bridge/file access, exported components
    এবং destructive migration review।
-   Baseline APK build ও smoke test।

**Acceptance:** clean build succeeds; existing core voice/chat screen
চালু হয়; known issues নথিভুক্ত।

### Phase 1 --- Central Menu / Feature Hub

-   `FeatureId`, `FeatureCatalog`, menu screen, routing।
-   Existing features-কে catalog-এ যুক্ত করা।
-   Placeholder screen-এ "Not implemented yet" দেখানো; dead buttons নয়।
-   Search/favorites/permission status indicator।
-   UI tests: menu open/close, navigation, back behavior, small screen
    scrolling।

**Acceptance:** সব পুরোনো ও নির্বাচিত নতুন feature একটি menu-তে দৃশ্যমান;
disabled feature স্পষ্টভাবে চিহ্নিত।

### Phase 2 --- Permission Dashboard & Security foundation

-   Runtime permission request flow।
-   Special access status check ও Settings links।
-   Action confirmation policy।
-   Safe audit log।
-   API secret migration plan ও WebView security review।

**Acceptance:** deny/revoke permission-এ crash হয় না; secret logs-এ নেই।

### Phase 3 --- Voice, offline commands & HUD

-   Voice state machine।
-   Bengali/English recognition adapter।
-   Offline allowlisted commands।
-   Wake word experiment এবং battery measurement।
-   Orb, waveform, lifecycle-aware animations।
-   Foreground service notification ও stop action।

**Acceptance:** app foreground, background, screen-off, permission
revoked এবং low-battery cases test pass।

### Phase 4 --- Phone controls & lock

-   App/settings intents।
-   Supported controls।
-   Device Admin explanation/activation।
-   `lockNow()` integration।
-   Unlock-এর জন্য normal Android Keyguard flow।
-   Unsupported device/API behavior fallback।

**Acceptance:** device lock works only after user activates admin;
unlock flow does not bypass system security।

### Phase 5 --- AI Chat & Memory

-   Gemini client/backend security।
-   Room migrations ও memory CRUD।
-   User-approved memory prompt context।
-   Error/retry/quota handling।
-   Memory delete/export test।

**Acceptance:** offline/no-key/timeout/quota states clearly handled;
delete action actually removes selected records.

### Phase 6 --- Automation

-   Allowlisted `ActionRegistry`।
-   Workflow create/edit/validate/run।
-   Risk-based confirmation।
-   Cancellation, timeout, run history।
-   Integration with voice parser and menu।

**Acceptance:** unrecognized or high-impact AI-suggested action never
executes without validation/required confirmation.

### Phase 7 --- Notifications & documents

-   Notification Listener access setup।
-   Per-app allowlist and sensitive notification controls।
-   Photo Picker/SAF import।
-   OCR/PDF extraction and preview।
-   Explicit upload confirmation for cloud processing।

**Acceptance:** no notification/document content is uploaded or stored
unexpectedly.

### Phase 8 --- Planner & diagnostics

-   Reminder CRUD and scheduler।
-   Exact alarm decision by actual use-case।
-   Network/battery/storage status।
-   Error report without secrets।
-   Reboot/time-change tests if scheduled work persists.

### Phase 9 --- Web research, translation, backup

-   Search/translation providers and source list。
-   Data export/import with schema version。
-   Encryption and restore validation。
-   Backup conflict and corrupted file tests。

### Phase 10 --- Release hardening

-   Release build, minified build if used, ProGuard/R8 checks।
-   Privacy policy and in-app data controls。
-   Battery/performance tests。
-   Accessibility and small-screen UI tests।
-   Android versions and multiple OEM test matrix。
-   Crash/error telemetry only with consent and no sensitive payloads।

------------------------------------------------------------------------

## 11. Test Plan

### 11.1 Functional tests

-   Menu button সব screen থেকে expected behavior করে।
-   প্রতিটি menu item সঠিক feature screen-এ route করে।
-   Feature enable/disable state persist হয়।
-   Voice বাংলা/English commands এবং unsupported command সঠিকভাবে
    সামলায়।
-   Offline mode-এ local command কাজ করে; network-only feature graceful
    error দেখায়।
-   Lock feature Device Admin inactive থাকলে activation prompt দেখায়।
-   Unlock request system Keyguard flow-তে যায়।
-   Memory CRUD, search, delete ও migration কাজ করে।
-   Automation step order, cancellation, timeout এবং confirmation ঠিকমতো
    কাজ করে।
-   Notification allowlist, draft reply ও notification access revoked
    behavior।
-   OCR/PDF import, malformed file, large file এবং no-network cases।
-   Reminder recurrence, notification permission, time zone ও reboot
    behavior।
-   Search source URLs, translation language selection এবং API failure
    handling।
-   Backup export/import, corrupted backup, duplicate records ও partial
    restore।

### 11.2 Permission tests

প্রতিটি sensitive permission/access-এর জন্য: 1. প্রথমবার অনুমতি দিন। 2.
অনুমতি deny করুন। 3. "Don't ask again"/Settings route প্রযোজ্য হলে পরীক্ষা
করুন। 4. Settings থেকে পরে revoke করুন। 5. Permission revoke অবস্থায় app
resume করুন। 6. App crash বা silent failure হচ্ছে কি না দেখুন।

### 11.3 Security tests

-   APK/resources/logcat-এ API key আছে কি না।
-   WebView untrusted URL/file access ও JavaScript bridge boundaries।
-   Exported activity/receiver external intent validation।
-   Workflow injection বা unknown action rejection।
-   Restore file size/type/schema validation।
-   Notification/document contents log/export না হওয়া।
-   Memory delete ও retention policy।
-   Confirmation ছাড়া high-impact action execute না হওয়া।

### 11.4 Performance tests

-   Voice idle battery drain।
-   Orb animation CPU/GPU load।
-   Large PDF memory usage।
-   Room query responsiveness।
-   Slow API/network timeout।
-   Foreground service notification consistency।
-   App background/foreground cycles ও process recreation।

### 11.5 Build / release tests

-   Clean Gradle build।
-   Debug install/launch।
-   Release signing configuration।
-   Database migration from previous installed version।
-   Android API levels: minimum supported, Android 12, 13, 14 এবং
    target/current device version অনুযায়ী পরীক্ষা।
-   Real device test---বিশেষ করে microphone, Device Admin, Notification
    Access ও OEM battery restrictions।

------------------------------------------------------------------------

## 12. Definition of Done

একটি feature সম্পূর্ণ বলা যাবে যখন: - Menu থেকে খোলা যায়। - UI state ও
error state আছে। - Permission/setup guidance আছে। - Runtime
revoke/denial সামলায়। - Room/API changes migration ও error handling
সহ। - Tests লেখা/চালানো হয়েছে। - Privacy impact documented। -
Battery/performance প্রভাব যাচাই হয়েছে। - Feature বাস্তবে test করা হয়েছে;
শুধু code লেখা বা build হওয়াকে feature success বলা হয় না।

------------------------------------------------------------------------

## 13. কোন ফিচার আগে বাস্তবায়ন করা সবচেয়ে ভালো?

**প্রথম release-এর সুপারিশ:** 1. Central Menu / Feature Hub 2. Permission
& Privacy Dashboard 3. Existing Voice/AI integration-এর cleanup 4.
Bengali/offline basic commands 5. Phone control + secure lock flow 6.
Room memory + safe API handling 7. Basic workflow automation 8. HUD
state animations

এরপর Notification Assistant, OCR/PDF, Planner, Diagnostics, Web Research
এবং Backup/Restore যোগ করো। এতে সব feature একসঙ্গে যোগ করে build ভেঙে
যাওয়ার ঝুঁকি কমবে।

------------------------------------------------------------------------

## 14. Developer checklist

-   [ ] ZIP backup / Git commit
-   [ ] Package name, `minSdk`, `targetSdk`, Gradle/JDK যাচাই
-   [ ] Existing menu/navigation পুনঃব্যবহার
-   [ ] Feature Catalog ও centralized router
-   [ ] Permission dashboard
-   [ ] Voice state machine ও Bengali model verification
-   [ ] Foreground microphone service rules
-   [ ] Device Admin activation + `lockNow()`
-   [ ] Keyguard unlock flow; no bypass
-   [ ] Gemini key moved out of shipped client secret storage
-   [ ] Room migrations; destructive fallback removed from production
    path
-   [ ] Workflow allowlist + confirmation policy
-   [ ] Notification Listener opt-in
-   [ ] Photo Picker / SAF / OCR / PDF import
-   [ ] Reminder scheduler + exact alarm review
-   [ ] Diagnostics without privileged claims
-   [ ] Search/translation source links
-   [ ] Encrypted/selective backup & restore
-   [ ] Unit, integration, permission, security, battery and real-device
    tests
-   [ ] Privacy policy, data deletion/export and release build

------------------------------------------------------------------------

## 15. গুরুত্বপূর্ণ সীমাবদ্ধতা

-   সাধারণ Android app system lock PIN/Pattern bypass করতে পারে না।
-   Android সব app-কে সব সময় background microphone ব্যবহার করতে দেয় না।
-   Wi-Fi/Bluetooth/system settings-এর সরাসরি control Android
    version/OEM অনুযায়ী সীমিত।
-   Notification content sensitive; user consent ও allowlist আবশ্যক।
-   OCR, AI summary ও translation ভুল করতে পারে; result review করার সুযোগ
    রাখো।
-   Web content এবং AI output untrusted input; কখনোই সরাসরি privileged
    action হিসেবে execute কোরো না।
-   এই plan-এর code snippets template। Existing project-এ integration,
    compile, device test ও debugging ছাড়া "সব feature ready" বলা যাবে
    না।

**শেষ কথা:** প্রথমে Feature Hub ও Permission Dashboard তৈরি করো। এরপর
একেকটি feature implement, build, test করে পরের ধাপে যাও। সব feature-এর
entry একই Menu Button-এ থাকবে, কিন্তু প্রতিটি feature তার নিজস্ব controller,
permission checks ও screen ব্যবহার করবে।
