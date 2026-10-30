# JARVIS Voice Assistant — আরও নতুন ফিচারের তালিকা ও Implementation Guide

**প্রজেক্ট:** বিদ্যমান Kotlin Android অ্যাপ (`com.jarvis.assistant`)  
**AI strategy:** Cloud + Offline Hybrid  
**ব্যয় লক্ষ্য:** যতটা সম্ভব Free/On-device; প্রয়োজন হলে ঐচ্ছিক Paid API  
**এই নথির উদ্দেশ্য:** বর্তমান অ্যাপের ফিচার পুনরায় তালিকাভুক্ত না করে, এর ওপর যোগ করা যায় এমন অতিরিক্ত ও তুলনামূলক ইউনিক ফিচার সাজানো।

> **নোট:** `Part 7.zip`-এর ফাইল তালিকায় ক্লাসগুলো আছে বলে ধরে নেওয়া হয়েছে; প্রতিটি ক্লাসের সব আচরণ বাস্তব ডিভাইসে পরীক্ষা করা হয়েছে—এমন নয়। নিচের নতুন ক্লাস ও ফাইলের নাম প্রস্তাবিত। নতুন কাজ শুরুর আগে সংশ্লিষ্ট existing class এবং Gradle dependency যাচাই করতে হবে।

## ১. আগে থেকেই থাকা ভিত্তি — এগুলো নতুন ফিচার হিসেবে পুনরায় বানাবেন না

ZIP-এর ফাইল তালিকায় নিচের ফিচার-ভিত্তিগুলো পাওয়া যায়:

- **Voice/Audio:** `AudioRecorder.kt`, `AudioPlayer.kt`, `VoskModelManager.kt`, `OwnerVoiceManager.kt`, `VoiceEnrollmentActivity.kt`
- **AI/Chat:** `GeminiLiveWebSocket.kt`, `ChatRepository.kt`, `ChatFragment.kt`, `MainViewModel.kt`
- **Automation:** `JarvisAccessibilityService.kt`, `CommandParser.kt`, `CommandExecutor.kt`, `AgentTaskEngine.kt`, `SerializedActionExecutor.kt`, `ActionVerifier.kt`
- **Screen/UI understanding:** `ScreenCaptureManager.kt`, `UiGroundingEngine.kt`, `VisualTaskOrchestrator.kt`, `ScreenSignatureCalculator.kt`
- **Memory:** `MemoryEntity.kt`, `MemoryDao.kt`, `MemoryDatabase.kt`, `MemoryManager.kt`, `MemoryRepository.kt`
- **App integrations:** `WhatsAppAssistant.kt`, `YouTubeAutomationManager.kt`, `YouTubeApiClient.kt`, contact/phone control helpers
- **অন্যান্য:** `LocationWeatherHelper.kt`, `HandsFreeActivity.kt`, `PrivacyShieldActivity.kt`, `OverlayAvatarActivity.kt`, `PersonaActivity.kt`, theme/settings ও chat history

নতুন ফিচারগুলোর আগে মূল voice flow, Cloud connection, data migration এবং permission behavior regression test দিয়ে যাচাই করা উচিত।

---

## ২. সবচেয়ে মূল্যবান নতুন ফিচারসমূহ

### Feature 1 — বাংলা ও Banglish Voice Pack

**Priority:** P0 / খুব বেশি  
**উপকার:** বাংলাদেশের ব্যবহারকারী বাংলা, Banglish এবং সহজ English command দিয়ে JARVIS ব্যবহার করতে পারবেন।  
**খরচ:** Local recognition-এর জন্য ফ্রি, কিন্তু ভালো Bengali offline recognition model খুঁজে/ইন্টিগ্রেট/টেস্ট করতে পরিশ্রম লাগবে; Cloud recognition ঐচ্ছিক এবং ব্যবহারভিত্তিক খরচ হতে পারে।

**কী করবে**
- “জার্ভিস, ইউটিউব খোলো”, “Jarvis WhatsApp kholo”, “কাল সকাল আটটায় মনে করিয়ে দিও”—এই ধরনের মিশ্র কমান্ড বোঝার চেষ্টা করবে।
- command alias: ` kholo/open`, `bondho/close`, `mone koriye/remind` ইত্যাদি normalize করবে।
- confidence কম হলে action না চালিয়ে ব্যবহারকারীকে confirm করতে বলবে।
- Settings থেকে ভাষা ও fallback behavior বেছে নেওয়া যাবে।

**প্রস্তাবিত ফাইল**
- নতুন: `audio/LanguagePackManager.kt`, `automation/command/CommandNormalizer.kt`, `audio/RecognitionLanguage.kt`
- পরিবর্তন: `VoskModelManager.kt`, `CommandParser.kt`, `AppPreferences.kt`, `SettingsFragment.kt`

**বাস্তবায়ন নোট**
- ZIP-এ `model-en-us` রয়েছে, তাই বর্তমান Vosk asset-কে Bangla offline speech recognition হিসেবে ধরে নেওয়া যাবে না। Bengali model আলাদা করে যাচাই করতে হবে।
- Language pack version, size, download source, checksum ও delete control রাখুন।
- বাংলা text input/command parsing আগে যোগ করা সম্ভব; offline speech model পরে যোগ করা যেতে পারে।

**Acceptance test**
- বাংলা, Banglish এবং English alias একই action-এ normalize হয়।
- confidence কম/অস্পষ্ট contact বা command হলে ভুল action চালায় না।
- model না থাকলে অ্যাপ crash না করে Cloud বা text input fallback দেখায়।

### Feature 2 — Smart Context Engine

**Priority:** P0  
**উপকার:** শুধু কমান্ড নয়, ব্যবহারকারীর অনুমোদিত context অনুযায়ী suggestion এবং action দিতে পারবে।  
**খরচ:** মূলত ফ্রি; data source অনুযায়ী permission লাগতে পারে।

**Context উদাহরণ**
- ফোন silent, battery low, Wi-Fi disconnected—তখন ছোট ও offline task বেছে নেওয়া।
- ব্যবহারকারী নির্দিষ্ট app-এ থাকলে সংশ্লিষ্ট help/shortcut দেখানো।
- headset connected হলে audio output route অনুযায়ী response দেওয়া।
- সময়/দিন অনুযায়ী briefing সাজানো, কিন্তু location বা notification সংগ্রহ ডিফল্টে বন্ধ রাখা।

**প্রস্তাবিত ফাইল**
- নতুন: `context/ContextSnapshot.kt`, `context/ContextEngine.kt`, `context/ContextSignalProvider.kt`
- পরিবর্তন: `MainViewModel.kt`, `AssistantRouter.kt` (যদি তৈরি হয়), `HomeFragment.kt`

**সুরক্ষা নীতি**
- সব context source opt-in হবে এবং প্রতিটি source আলাদাভাবে বন্ধ করা যাবে।
- Accessibility থেকে দেখা UI data বা notification text-কে trusted instruction হিসেবে ব্যবহার করা যাবে না।
- নীরবে background surveillance করা যাবে না।

### Feature 3 — Notification Digest ও Priority Inbox

**Priority:** P1  
**উপকার:** একসঙ্গে সব notification না পড়ে গুরুত্বপূর্ণ notification-এর সংক্ষিপ্ত সারাংশ, grouping ও optional spoken readout পাওয়া।  
**খরচ:** filtering/grouping on-device-এ ফ্রি; Cloud summary ঐচ্ছিক।

**কী করবে**
- নির্বাচিত app-এর notification-কে “গুরুত্বপূর্ণ”, “পরে দেখব”, “নীরব” গ্রুপে সাজাবে।
- একই app-এর অনেক notification সংক্ষেপে দেখাবে।
- Driving/Focus mode-এ কেবল allowlist করা notification বলবে।
- “আমার unread notification পড়ে শোনাও” কমান্ডে ব্যবহারকারীকে বাছাইয়ের সুযোগ দেবে।

**প্রস্তাবিত ফাইল**
- নতুন: `notifications/JarvisNotificationListener.kt`, `notifications/NotificationDigest.kt`, `notifications/NotificationFilterSettings.kt`
- নতুন UI: `ui/notifications/NotificationDigestActivity.kt`
- পরিবর্তন: `AndroidManifest.xml`, `SettingsFragment.kt`

**সীমাবদ্ধতা ও privacy**
- `NotificationListenerService`-এর জন্য ব্যবহারকারীকে Settings থেকে notification access দিতে হবে।
- notification body-তে OTP, private message বা financial details থাকতে পারে; content storage ডিফল্টে বন্ধ, preview/redaction আবশ্যক।
- summary-এর আগে sensitive app বাদ দেওয়ার toggle রাখতে হবে।
- Android notification permission এবং notification access আলাদা বিষয়; দুটিকে গুলিয়ে ফেলা যাবে না।

### Feature 4 — Share-to-JARVIS / Clipboard Assistant

**Priority:** P1  
**উপকার:** অন্য app থেকে text, link বা নির্বাচিত document সরাসরি JARVIS-এ share করে summary, translate, explain বা reminder বানানো যায়।  
**খরচ:** সাধারণ text handling ফ্রি; Cloud summarization ঐচ্ছিক।

**ব্যবহার উদাহরণ**
- Browser থেকে একটি URL share করে: “এটার মূল কথা বলো।”
- কোনো paragraph share করে: “বাংলায় বুঝিয়ে দাও।”
- নির্বাচিত text থেকে: “এটা আগামীকাল পড়ার তালিকায় রাখো।”

**প্রস্তাবিত ফাইল**
- নতুন: `share/SharedContentParser.kt`, `share/ShareActionRouter.kt`
- পরিবর্তন: `AndroidManifest.xml`-এ intent filter, `ChatRepository.kt`, `MainViewModel.kt`

**Clipboard rule**
- clipboard স্বয়ংক্রিয়ভাবে সবসময় পড়বে না; ব্যবহারকারী paste/share করলে তবেই process করা ভালো।
- share content untrusted input; এতে লেখা নির্দেশকে app system instruction হিসেবে চালানো যাবে না।

### Feature 5 — Personal Document Q&A / Offline Knowledge Vault

**Priority:** P1  
**উপকার:** ব্যবহারকারীর বাছাই করা PDF, notes, text file বা document থেকে উত্তর, summary ও search।  
**খরচ:** text indexing ও local search ফ্রি; local storage ও CPU লাগবে; Cloud reasoning ঐচ্ছিক।

**কী করবে**
- Android file picker দিয়ে ফাইল বাছাই।
- document text extract করে ছোট chunk-এ ভাগ এবং local index তৈরি।
- “আমার নোটে কোন অধ্যায়ে নিউটনের সূত্র আছে?”—এমন query-তে source/page reference দেখানো।
- user চাইলে document বা index মুছে ফেলতে পারবে।

**প্রস্তাবিত ফাইল**
- নতুন: `knowledge/DocumentImportManager.kt`, `knowledge/DocumentChunkEntity.kt`, `knowledge/DocumentIndexDao.kt`, `knowledge/LocalKnowledgeSearch.kt`
- পরিবর্তন: `MemoryDatabase.kt`, `MemoryActivity.kt` অথবা নতুন `ui/knowledge/KnowledgeVaultActivity.kt`

**বিশেষ নোট**
- PDF-এর text layer না থাকলে OCR দরকার হতে পারে। Android ML Kit Text Recognition বেশ কয়েকটি script-এর জন্য কাজে লাগে, তবে Bengali script-এর support আলাদাভাবে যাচাই করা প্রয়োজন; Bangla support নিশ্চিত না করে প্রতিশ্রুতি দেবেন না।
- নোটের উত্তর হলে source filename/page দেখাতে হবে; source না পেলে অনুমানকে facts হিসেবে উপস্থাপন করা যাবে না।
- ডিফল্টে ফাইল Cloud-এ upload হবে না।

### Feature 6 — Real-World Camera Copilot

**Priority:** P2  
**উপকার:** স্ক্রিনের বাইরে বাস্তব জগতের printed text, sign, object বা ছোট নির্দেশনা বুঝতে সাহায্য করবে। বিদ্যমান screen capture এটির বিকল্প নয়—কারণ এটি camera input।  
**খরচ:** ML Kit-এর মতো on-device toolkit দিয়ে কিছু কাজ ফ্রি করা যায়; উন্নত image reasoning-এর Cloud API ব্যবহারভিত্তিক হতে পারে।

**কী করবে**
- ক্যামেরায় text ধরলে OCR করে copy/translate/read aloud।
- ব্যবহারকারী কোনো object বা appliance দেখিয়ে help চাইলে supported recognition দিয়ে গাইড করবে।
- document/receipt থেকে তারিখ বা amount draft-এ নিতে পারে—user confirmation ছাড়া final save নয়।

**প্রস্তাবিত ফাইল**
- নতুন: `vision/CameraCopilotController.kt`, `vision/OnDeviceTextRecognizer.kt`, `ui/vision/CameraCopilotActivity.kt`
- dependency: প্রয়োজনে CameraX ও ML Kit; project-এর minSdk/dependency compatibility আগে যাচাই করতে হবে।

**সুরক্ষা**
- camera চালুর আগে system permission এবং দৃশ্যমান capture state।
- face recognition বা sensitive trait inference-কে প্রয়োজন ছাড়া scope-এ আনবেন না।
- ছবি save/Cloud upload আলাদা opt-in হবে।

### Feature 7 — Workflow Recorder & Replay

**Priority:** P1  
**উপকার:** ব্যবহারকারী একবার অনুমোদিত task sequence চালিয়ে তা reusable workflow হিসেবে save করতে পারবেন। এটি সাধারণ “self-healing” থেকে আলাদা: এখানে focus হচ্ছে ব্যবহারকারীর নিজের routine record করে পুনরায় চালানো।

**কী করবে**
- “Record routine” চাপার পর action step ও তার ফলাফল capture।
- user workflow-কে নাম দিয়ে save করবেন, যেমন “পড়ার সময় প্রস্তুতি”।
- replay-এর আগে summary ও risky step confirmation।
- UI পরিবর্তিত হলে পুরোনো coordinate blind replay না করে element পুনরায় শনাক্ত করবে।

**প্রস্তাবিত ফাইল**
- নতুন: `automation/workflow/WorkflowRecorder.kt`, `WorkflowPlaybackEngine.kt`, `WorkflowValidation.kt`
- বিদ্যমান integration: `AgentTaskEngine.kt`, `SerializedActionExecutor.kt`, `ActionVerifier.kt`, `UiGroundingEngine.kt`

**সীমাবদ্ধতা**
- ব্যাংকিং, payment, password, OTP বা security settings-এর action replay করা যাবে না/প্রতিটি ক্ষেত্রে আলাদা explicit approval লাগবে।
- coordinates-এর বদলে semantic UI selector ও verification ব্যবহার করতে হবে।

### Feature 8 — Proof of Action / Task Receipt

**Priority:** P0  
**উপকার:** JARVIS শুধু “হয়ে গেছে” বলবে না; কী চেষ্টা করেছে, কী যাচাই হয়েছে এবং কোন ধাপে ব্যর্থ হয়েছে তা দেখাবে। এটি অত্যন্ত ইউনিক এবং বিশ্বাসযোগ্যতা বাড়ায়।

**কী দেখাবে**
- Task ID, শুরু/শেষের সময়, action list, success/failure result।
- `Verified`, `Partially verified`, `Not verified` status।
- evidence হিসেবে সংক্ষিপ্ত accessibility state, allowed API response বা user-approved screenshot reference।
- sensitive content বাদ দিয়ে error reason ও retry count।

**প্রস্তাবিত ফাইল**
- নতুন: `automation/agent/TaskReceipt.kt`, `data/audit/ActionLogEntity.kt`, `data/audit/ActionLogDao.kt`, `ui/audit/TaskReceiptActivity.kt`
- বিদ্যমান: `ActionVerifier.kt`, `AgentTaskEngine.kt`, `TaskProgressOverlay.kt`

**Acceptance rule**
- verifier কোনো নিশ্চিত ফল না দিলে “সফল” বলা যাবে না।
- proof-এ password, OTP বা private message body রাখা যাবে না।
- user log clear/export করতে পারবেন।

### Feature 9 — Risk-Based Voice Confirmation

**Priority:** P1  
**উপকার:** “Owner Voice” feature-কে শুধু voice match না রেখে action-এর ঝুঁকি অনুযায়ী confirmation-এর সঙ্গে যুক্ত করবে।

**কী করবে**
- low-risk action: voice command যথেষ্ট হতে পারে।
- medium-risk: confirmation phrase বা on-screen confirm।
- high-risk: Android system authentication/biometric prompt যেখানে উপযুক্ত, অথবা manual confirmation।
- voice match uncertain হলে action refuse করবে।

**প্রস্তাবিত ফাইল**
- নতুন: `security/RiskLevel.kt`, `security/VoiceActionPolicy.kt`, `security/StepUpAuthentication.kt`
- বিদ্যমান: `OwnerVoiceManager.kt`, `VoiceEnrollmentActivity.kt`, `TaskPolicyEngine.kt`

**গুরুত্বপূর্ণ:** Voice recognition একা শক্তিশালী identity proof নয়; replayed audio বা কাছাকাছি কণ্ঠের ভুল match সম্ভব। Payment, account security বা secret retrieval শুধু voice match-এ অনুমোদন করা যাবে না।

### Feature 10 — Device Health & Troubleshooting Assistant

**Priority:** P1  
**উপকার:** “ফোনে storage কম কেন?”, “JARVIS কেন voice শুনছে না?”—এ ধরনের সমস্যায় নিরাপদ diagnostic report দিতে পারবে।

**কী করবে**
- microphone permission, notification access, accessibility service state, network state, battery/storage availability-এর basic diagnosis।
- permission বন্ধ থাকলে সরাসরি permission চালু না করে Settings-এর সঠিক screen খুলে নির্দেশনা দেবে।
- error code থেকে troubleshooting checklist দেখাবে।
- optional diagnostic report user review করার পরে export হবে।

**প্রস্তাবিত ফাইল**
- নতুন: `diagnostics/DeviceHealthChecker.kt`, `diagnostics/DiagnosticReport.kt`, `ui/diagnostics/DiagnosticsActivity.kt`
- পরিবর্তন: `PermissionHelper.kt`, `PermissionActivity.kt`, `SettingsFragment.kt`

**সুরক্ষা:** ব্যবহারকারীর সম্মতি ছাড়া installed app list, private logs বা অন্য app-এর content সংগ্রহ করবে না।

### Feature 11 — Focus / Driving / Meeting Profiles

**Priority:** P1  
**উপকার:** আলাদা profile অনুযায়ী voice response, notification readout, overlay এবং confirmation behavior বদলাবে।

**Profile উদাহরণ**
- `Focus`: শুধু নির্ধারিত reminder এবং urgent allowed notification।
- `Driving`: বড় UI, short spoken reply, কম interaction; ফোনে মনোযোগ কমাতে voice-only workflow-এর সীমা বজায় রাখা।
- `Meeting`: proactive speech বন্ধ, quick notes ও silent reminder।
- `Battery Saver`: local command প্রাধান্য, ভারী animation ও unnecessary Cloud request কমানো।

**প্রস্তাবিত ফাইল**
- নতুন: `profiles/AssistantProfile.kt`, `profiles/ProfileManager.kt`, `ui/settings/ProfileSelectionActivity.kt`
- পরিবর্তন: `AppPreferences.kt`, `AudioPlayer.kt`, `MainViewModel.kt`, `PrivacyShieldActivity.kt`

### Feature 12 — Voice Journal → Task Extraction

**Priority:** P2  
**উপকার:** “আজকের কাজগুলো লিখে রাখো: গণিত পড়ব, রিপোর্ট জমা দেব”—এই কথাকে note এবং checklist-এ রূপান্তর করবে।

**কী করবে**
- voice note সংরক্ষণ (default local)।
- transcript থেকে সম্ভাব্য task, date ও reminder draft।
- user review ছাড়া extracted task automatic schedule করবে না।
- note/search/tag/export/delete সুবিধা।

**প্রস্তাবিত ফাইল**
- নতুন: `journal/VoiceJournalEntity.kt`, `VoiceJournalDao.kt`, `TaskExtractionParser.kt`, `ui/journal/VoiceJournalActivity.kt`
- বিদ্যমান integration: `AudioRecorder.kt`, `MemoryRepository.kt`

### Feature 13 — Offline Capability Packs ও Model Manager

**Priority:** P1  
**উপকার:** ব্যবহারকারী বুঝবেন কোন কাজ internet ছাড়াই সম্ভব; প্রয়োজনমতো language/model pack download বা delete করতে পারবেন।

**কী করবে**
- Installed pack list, version, storage size, checksum এবং last-used date।
- “Offline-এ কাজ করবে” বা “Cloud required” capability badge।
- model update failure হলে আগের working model রাখবে।
- mobile data-তে বড় model download default off।

**প্রস্তাবিত ফাইল**
- নতুন: `models/ModelPack.kt`, `models/ModelPackManager.kt`, `ui/settings/OfflinePacksActivity.kt`
- বিদ্যমান: `VoskModelManager.kt`, `AppPreferences.kt`

### Feature 14 — Custom Skill Cards (কোড না লিখে ছোট automation)

**Priority:** P2  
**উপকার:** ব্যবহারকারী নিজের সহজ command alias বা fixed sequence তৈরি করতে পারবেন, যেমন “Study mode on”।

**কী করবে**
- trigger phrase, allowed actions, confirmation policy ও optional schedule।
- skill import/export-এর সময় schema validation।
- built-in allowlist action-এর বাইরে arbitrary code execution নয়।
- disable/version/rollback control।

**প্রস্তাবিত ফাইল**
- নতুন: `skills/SkillDefinition.kt`, `SkillRegistry.kt`, `SkillValidator.kt`, `ui/skills/SkillStudioActivity.kt`
- বিদ্যমান: `CommandParser.kt`, `CommandExecutor.kt`, `AgentTaskEngine.kt`

### Feature 15 — Connected Calendar / Email Briefing (ঐচ্ছিক)

**Priority:** P2  
**উপকার:** “আজ আমার কী কী কাজ?” প্রশ্নে calendar event বা নির্বাচিত email metadata থেকে briefing তৈরি করা।

**কী করবে**
- প্রথমে শুধু read-only calendar integration।
- Email summary-এর আগে account connect, scopes review ও per-provider permission।
- event create/send email-এর আগে confirmation; ভুল recipient বা সময় থাকলে action বন্ধ।
- sync data-এর retention limit ও Disconnect/Delete control।

**প্রস্তাবিত ফাইল**
- নতুন: `integrations/calendar/CalendarConnector.kt`, `integrations/email/EmailConnector.kt`, `integrations/ConnectedAccountSettings.kt`
- প্রয়োজন: provider OAuth/connector এবং token protection; API/permission ও policy আগে যাচাই করতে হবে।

**ব্যয়:** API/provider অনুযায়ী ফ্রি সীমা থাকতে পারে; quota বা paid usage হতে পারে। এই ফিচারটি core offline app-এর জন্য বাধ্যতামূলক নয়।

### Feature 16 — Smart Home Integration

**Priority:** P3  
**উপকার:** লাইট, ফ্যান বা smart plug-এর মতো supported device voice command দিয়ে নিয়ন্ত্রণ।

**প্রস্তাবিত পথ**
- Home Assistant-এর মতো user-controlled hub-এর documented API integration আগে বিবেচনা।
- local network support থাকলে local-first connection।
- device allowlist, explicit confirmation এবং “সব বন্ধ করো” এর মতো command-এর confirmation policy।
- hub unavailable হলে failure স্পষ্ট দেখানো।

**প্রস্তাবিত ফাইল**
- নতুন: `integrations/smarthome/SmartHomeConnector.kt`, `SmartDevice.kt`, `SmartHomePolicy.kt`

**ব্যয়:** hub/device কিনতে খরচ হতে পারে; cloud subscription বাধ্যতামূলক কি না chosen platform-এর ওপর নির্ভর করে।

### Feature 17 — Energy-Aware AI Router

**Priority:** P1  
**উপকার:** battery কম, data সীমিত বা নেটওয়ার্ক দুর্বল হলে lightweight local response অগ্রাধিকার পাবে।

**কী করবে**
- battery/network status অনুযায়ী routing preference।
- ব্যবহারকারী প্রতি দিন সর্বোচ্চ Cloud request বা budget limit সেট করতে পারবেন।
- request পাঠানোর আগে আনুমানিক Cloud dependency জানাবে; খরচের নিশ্চয়তা হিসেবে নয়।
- quota exhausted হলে local fallback বা “এখন সম্ভব নয়” response।

**প্রস্তাবিত ফাইল**
- নতুন: `core/ResourceBudgetManager.kt`, `core/NetworkStateProvider.kt`, `core/CloudUsageTracker.kt`
- বিদ্যমান: `GeminiLiveWebSocket.kt`, `AppPreferences.kt`, `VoskModelManager.kt`

### Feature 18 — Accessibility & Voice Debug Lab

**Priority:** P1 (Developer/debug build-এ)  
**উপকার:** voice না শোনা, action miss হওয়া বা permission problem ডিবাগ করা সহজ হবে।

**কী দেখাবে**
- last recognized transcript এবং confidence (যেখানে পাওয়া যায়)।
- parsed command, selected engine, action state ও failure reason।
- redacted network status/response code; API key বা user secrets নয়।
- mock command দিয়ে unit/integration test।

**প্রস্তাবিত ফাইল**
- নতুন: `debug/JarvisDiagnosticsPanel.kt`, `debug/DebugEvent.kt`
- পরিবর্তন: `AgentTaskEngine.kt`, `CommandParser.kt`, `GeminiLiveWebSocket.kt`

**সুরক্ষা:** release build-এ debug panel default disabled; ব্যক্তিগত transcript logging opt-in এবং redacted হবে।

### Feature 19 — Conversation Memory Controls 2.0

**Priority:** P1  
**উপকার:** memory ব্যবহারকারীকে আরও স্বচ্ছ control দেবে। এটি বর্তমান Memory feature-এর replacement নয়, বরং governance layer।

**কী করবে**
- “এটা মনে রেখো”, “এটা ভুলে যাও”, “শুধু এই conversation-এর জন্য” command।
- memory item-এর source ও last updated date দেখা।
- auto-memory off, pause, export, selective delete।
- ভুল memory-এর জন্য edit/correct action।

**প্রস্তাবিত ফাইল**
- বিদ্যমান: `MemoryEntity.kt`, `MemoryManager.kt`, `MemoryRepository.kt`, `MemoryActivity.kt`
- নতুন: `data/memory/MemoryRetentionPolicy.kt`, `MemoryExportManager.kt`

### Feature 20 — Action Undo / Safe Rollback

**Priority:** P1  
**উপকার:** ভুল workflow থেকে ফিরতে সাহায্য করবে।

**কী করবে**
- শুধু যে action নির্ভরযোগ্যভাবে reversible, সেগুলোর জন্য undo record।
- উদাহরণ: JARVIS-এর নিজের তৈরি reminder বন্ধ করা, draft workflow rollback, app-এর নিজের data edit undo।
- WhatsApp message পাঠানো বা phone call-এর মতো action-কে “undo করা যাবে” বলে ভুল দাবি করবে না।
- action শুরু করার আগে reversibility metadata দেখাবে।

**প্রস্তাবিত ফাইল**
- নতুন: `automation/undo/UndoAction.kt`, `UndoRegistry.kt`, `UndoManager.kt`
- integration: `ActionVerifier.kt`, `ActionLogDao.kt`, `WorkflowRepository.kt`

---

## ৩. অগ্রাধিকারভিত্তিক বাস্তবায়ন তালিকা

| ধাপ | ফিচার | কেন এই ক্রমে |
|---|---|---|
| 1 | Proof of Action / Task Receipt | automation-এর ফলাফল নিয়ে আস্থা বাড়ায়; অন্য feature-ও এটিকে ব্যবহার করবে |
| 2 | বাংলা/Banglish command support | স্থানীয় ব্যবহারকারীর জন্য সবচেয়ে বড় ব্যবহারযোগ্যতার উন্নতি |
| 3 | Smart Context + Energy-Aware Router | Cloud খরচ, latency ও অপ্রয়োজনীয় action কমায় |
| 4 | Share-to-JARVIS | কম জটিলতায় দৈনন্দিন কাজে বড় সুবিধা |
| 5 | Workflow Recorder & Replay | বিদ্যমান AgentTaskEngine/Accessibility foundation কাজে লাগায় |
| 6 | Notification Digest | opt-in privacy controls সহ productivity বাড়ায় |
| 7 | Device Health & Debug Lab | সমস্যা খুঁজে বের করা ও support সহজ করে |
| 8 | Offline Knowledge Vault | user-owned notes/documents থেকে grounded answer |
| 9 | Focus Profiles + Voice Journal | ব্যক্তিগত workflow ও productivity |
| 10 | Camera Copilot | screen automation-এর বাইরের visual capability |
| 11 | Calendar/Email connectors | optional account integration; extra scopes ও API management দরকার |
| 12 | Smart Home | hardware/provider dependency বেশি, তাই পরে |

## ৪. প্রস্তাবিত নতুন UI menu

Settings/Home-এ বড় নতুন tab না বাড়িয়ে নিচের বিভাগে ফিচারগুলো গুছিয়ে দেওয়া যায়:

- **Language & Offline Packs:** language, model download, offline capabilities
- **Context & Profiles:** Focus, Driving, Meeting, Battery Saver
- **Automation Lab:** Recorder, custom skill, debug panel (debug build)
- **Knowledge Vault:** documents, voice journal, saved notes
- **Notifications:** app allowlist, digest, sensitive content hiding
- **Privacy & Trust:** data retention, cloud routing, action receipt, export/delete
- **Connections:** Calendar/Email/Smart Home—শুধু ব্যবহারকারী ইচ্ছা করলে

বিদ্যমান XML/ViewBinding layout বজায় রাখুন; শুধু এসব feature-এর জন্য পুরো UI Jetpack Compose-এ migrate করা আবশ্যক নয়।

## ৫. নতুন database table-এর প্রস্তাব

নতুন সবকিছু যোগ করার আগে `MemoryDatabase.kt`-এর বর্তমান version, entities ও migration পরীক্ষা করুন। Destructive migration ব্যবহার করে user data মুছে যাওয়ার ঝুঁকি এড়াতে explicit Room migration ও migration tests যোগ করুন।

| Table | মূল field-এর উদাহরণ | উদ্দেশ্য |
|---|---|---|
| `action_logs` | `taskId`, `actionType`, `status`, `time`, `redactedEvidence` | Task receipt ও audit |
| `workflows` | `id`, `name`, `version`, `enabled` | saved routine |
| `workflow_steps` | `workflowId`, `position`, `actionType`, `argumentsJson` | ধাপে ধাপে action |
| `notification_rules` | `packageName`, `priority`, `redactContent` | notification filtering |
| `document_sources` | `id`, `displayName`, `mimeType`, `importedAt` | user-selected documents |
| `document_chunks` | `sourceId`, `chunkIndex`, `text`, `pageNumber` | local search/grounding |
| `voice_journal` | `id`, `transcript`, `createdAt`, `tagsJson` | local voice notes |
| `skill_definitions` | `id`, `version`, `actionsJson`, `enabled` | custom skill registry |
| `model_packs` | `id`, `version`, `checksum`, `installedAt` | language/model management |

`argumentsJson`, `actionsJson` বা import করা workflow সবসময় schema-validate ও allowlist-check করতে হবে। Database-এ থাকা string-কে executable code ধরে চালানো যাবে না।

## ৬. API ও Android dependency strategy

### Local-first (আগে)
- Vosk/বর্তমান local parser
- Room database
- Android file picker ও local text search
- WorkManager দিয়ে persistent/deferred work
- ML Kit-এর supported on-device vision/OCR use case

### Cloud-optional
- Gemini Live/approved Gemini API function calling
- কেবল প্রয়োজনীয় prompt/context পাঠানো
- usage/cost tracker ও daily cap
- API key-কে public APK-তে hardcode না করা; suitable server-side proxy বা supported Firebase AI integration এবং abuse protections বিবেচনা করা

### Integration policy
- প্রতিটি optional integration-এর জন্য আলাদা connect/disconnect control।
- OAuth token বা API secrets সাধারণ preference/log-এ রাখা যাবে না।
- External model function call-কে direct executable instruction হিসেবে নয়, typed proposal হিসেবে নিতে হবে; app-side policy ও confirmation-এর পরে action চালাতে হবে।

## ৭. Security / Privacy checklist

- [ ] API key, OAuth token, OTP, password log/database-এ নেই।
- [ ] `WebView`-এ প্রয়োজন না থাকলে `file://` access বন্ধ; local assets-এর জন্য `WebViewAssetLoader` বিবেচনা।
- [ ] Screen, camera, accessibility, notification access-এর কাজ আগে ব্যাখ্যা করে user-controlled enable/disable।
- [ ] Cloud-এ transcript/screenshot পাঠানোর আগে privacy rule এবং প্রয়োজনীয় disclosure/consent।
- [ ] UI-তে পাওয়া text, webpage, notification ও imported document-কে untrusted data ধরা।
- [ ] Message send, call, account change বা external side-effect-এর আগে confirmation policy।
- [ ] Stop button নতুন action বন্ধ করে এবং queue state সঠিকভাবে update করে।
- [ ] Memory, notification history, document index ও action log export/delete করা যায়।
- [ ] Release build-এ verbose debug log বন্ধ এবং sensitive field redacted।
- [ ] Google Play release করলে Accessibility API এবং user data সম্পর্কিত বর্তমান Play policy যাচাই।

## ৮. Test plan

### Unit tests
- Bengali/Banglish alias normalization ও ambiguous command rejection
- Smart context routing ও budget limits
- Action risk level → required confirmation mapping
- Workflow JSON schema validation
- Retry count limit ও no-infinite-loop invariant
- Notification redaction এবং priority filtering
- Document search result-এর সঙ্গে source/page mapping

### Integration tests
- Offline mode-এ network ছাড়া local command
- Cloud timeout/quota হলে graceful fallback
- Notification access বন্ধ/পুনরায় চালু করা
- File import, cancellation, corrupt document, index deletion
- Workflow replay-এর সময় UI structure বদলে গেলে stop/re-ground
- Room migration-এ পুরোনো memory অক্ষত থাকা
- Scheduled task reboot/process restart-এর পর consistent থাকা

### Manual device tests
- Low battery, airplane mode, slow network
- Microphone/camera/notification/accessibility permission revoke
- Screen capture consent cancel
- Bengali accent, noise, mixed Bangla-English speech
- App background/foreground এবং screen lock
- Message/call action-এর pre-confirmation ও stop behavior

### Release gate
নিচের সব শর্ত পাস না করলে release নয়:
1. Clean build সফল।
2. Existing voice/chat/YouTube/WhatsApp/memory smoke tests পাস।
3. Room migration test পাস।
4. API secret বা private content log-এ নেই।
5. High-risk action confirmation ছাড়া execute হয় না।
6. Verifier proof না দিলে app success claim করে না।

## ৯. বর্তমান Android documentation

- **WorkManager:** persistent/deferred background work এবং retry/work chaining — https://developer.android.com/develop/background-work/background-tasks/persistent
- **ML Kit Text Recognition:** on-device OCR ব্যবহারের ধাপ ও supported script যাচাই — https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- **NotificationListenerService:** notification event access-এর Android API — https://developer.android.com/reference/android/service/notification/NotificationListenerService
- **WebView Unsafe File Inclusion:** `file://` access ঝুঁকি এবং `WebViewAssetLoader` mitigation — https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
- **Gemini Function Calling:** model-কে typed function proposal ফেরাতে দেওয়ার পদ্ধতি — https://ai.google.dev/gemini-api/docs/function-calling

API, Android policy, quota এবং pricing পরিবর্তিত হতে পারে; implementation/release-এর আগে অফিসিয়াল ডকুমেন্টেশন আবার পরীক্ষা করুন।

## ১০. আমার চূড়ান্ত সুপারিশ

প্রথমে এই পাঁচটি জিনিস যোগ করুন:

1. **Proof of Action / Task Receipt** — JARVIS যা করেছে তার সত্যতা প্রমাণ করে।
2. **Bangla + Banglish commands** — আপনার ব্যবহারকারীর জন্য বাস্তব সুবিধা।
3. **Share-to-JARVIS** — অন্য app থেকে text/link নিয়ে কাজ করা।
4. **Workflow Recorder & Replay** — পুনরাবৃত্ত কাজকে এক কমান্ডে চালানো।
5. **Offline Knowledge Vault** — নিজের documents থেকে source দেখিয়ে উত্তর পাওয়া।

এগুলোর পর Notification Digest, Smart Context, Device Health, Focus Profile এবং Camera Copilot যোগ করুন। প্রতিটি feature আলাদা branch/commit-এ যোগ করে পরীক্ষা করুন; সব feature একসঙ্গে যোগ করবেন না।
