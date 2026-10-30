# Wi‑Fi (LAN) ও Internet Remote Control: বাস্তবে কীভাবে কাজ করবে

JARVIS NEXUS প্রজেক্টের জন্য | ফিচার ও ফাংশন বাস্তবে কাজ করানোর গাইড

## ১. মূল কথা

আসল কাজ করানোর জন্য ৩টি জিনিস লাগবে:

1. **নিরাপদ পরিচয় ও সংযোগ।** Pairing, mutual TLS, session token, প্রতি command-এ permission চেক।
2. **Host ফোনে সত্যিকারের Android API।** Battery, MediaProjection, Photo Picker, MediaSession ইত্যাদি।
3. **Internet-এর জন্য একটা সার্ভার।** ফোনে port খুলে বা শুধু IP দিয়ে এটা নিরাপদে হয় না।

বর্তমান কোডে এই তিনটির মধ্যে প্রায় কোনোটাই সঠিকভাবে নেই। তাই আগে নিরাপত্তা, পরে ফিচার।

## ২. সংযোগ কীভাবে চলবে

### LAN (একই Wi‑Fi)

1. Host ফোন QR দেখায়। QR-এ থাকে Host-এর certificate fingerprint আর এক-বারের গোপন কোড (২ মিনিট মেয়াদ)।
2. Controller QR স্ক্যান করে Host-এর সঙ্গে TLS সংযোগ করে। Fingerprint না মিললে সংযোগ হয় না।
3. Host-এ "অনুমতি দেবেন?" দেখায়। Controller-এর নাম, দুই ফোনে মেলানোর কোড, আর কোন কোন capability চাইছে সব দেখানো হয়।
4. Host Allow দিলে দুই ফোন একে অপরের certificate সংরক্ষণ করে। এরপর থেকে নতুন কোড লাগে না। পরের সংযোগে দুই ফোন পরস্পরের certificate যাচাই করে।
5. Discovery (NSD/mDNS) শুধু Host খুঁজে পেতে সাহায্য করে। এতে কাউকে বিশ্বাস করা হয় না। ম্যানুয়াল IP/port থাকবে ব্যাকআপ হিসেবে।

### Internet (আলাদা নেটওয়ার্ক)

1. দুই ফোনই সার্ভারের সঙ্গে `wss://` সংযোগ করে (ফোনে কোনো port খুলতে হয় না)।
2. সার্ভার প্রতিটি ফোনের পরিচয় যাচাই করে: ফোন সার্ভারের পাঠানো challenge নিজের private key দিয়ে sign করে।
3. সার্ভার শুধু আগে pair হওয়া দুই ফোনের মধ্যে বার্তা পাঠায়।
4. বার্তা দুই ফোনের মধ্যে end-to-end encrypted থাকে। সার্ভার শুধু ciphertext দেখে।
5. Host অফলাইন থাকলে স্পষ্ট "Offline" দেখায়, ভুয়া সফলতা নয়।

**বাস্তব সীমা:** ফোনে ফোনে সরাসরি Internet সংযোগ (NAT/ক্যারিয়ার ফায়ারওয়ালের কারণে) প্রায়ই হয় না। তাই সার্ভার লাগবেই।

## ৩. প্রতিটি ফিচার বাস্তবে কীভাবে কাজ করে

| ফিচার | বাস্তব কাজ | Android-এর সীমা |
| --- | --- | --- |
| **Device status** | Battery, charging, network, Android সংস্করণ আসল API থেকে | সীমাবদ্ধ নেই |
| **Remote chat** | Controller-এর প্রশ্ন Host-এর আসল AI-তে যায়, আসল উত্তর ফেরত আসে। Host-এ "Remote prompt from X" দেখায় | ভুয়া reply এখন কোডে আছে, সেটা সরাতে হবে |
| **File পাঠানো** | Controller ফাইল বাছে → Host-এ নাম/সাইজ দেখিয়ে Accept → chunk করে পাঠানো → SHA‑256 মেলানো → মিললে সেভ | Host-এর পুরো স্টোরেজ ব্রাউজ করা যাবে না। শুধু ব্যবহারকারী যা বাছে বা Accept করে |
| **Gallery দেখা** | Host অনুমতি দিলে ছবি/ভিডিও তালিকা ও থাম্বনেইল | Android 13+ এ `READ_MEDIA_IMAGES/VIDEO` runtime permission লাগে |
| **Screen share** | Host-এ Android-এর সিস্টেম consent → ফ্রেম Controller-এ যায় → Controller-এ ছবি দেখা যায় → Host-এ Stop বোতাম | প্রতিবার ব্যবহারকারীর সম্মতি লাগে। Android 14-এ ব্যবহারকারী শুধু একটা অ্যাপ বেছে নিতে পারেন। গোপনে চালু করা যায় না |
| **Remote touch/টাইপ** | Accessibility Service দিয়ে `dispatchGesture` (ট্যাপ/সোয়াইপ) করা সম্ভব। এটাই AnyDesk-ধরনের নিয়ন্ত্রণ | ব্যবহারকারীকে নিজে Accessibility চালু করতে হয়। Play Store-এ কঠোর নীতি আছে। দৃশ্যমান সম্মতি ও নোটিফিকেশন ছাড়া করবেন না |
| **Back/Home/Recents** | Accessibility-র `performGlobalAction` | Accessibility চালু থাকতে হবে |
| **Media control** | সক্রিয় media session-এ play/pause/next | যে অ্যাপ media session দেয় শুধু সেগুলোতে কাজ করে |
| **Dialer** | `ACTION_DIAL` দিয়ে নম্বর বসিয়ে খোলা, Host নিজে Call চাপে | Android 10+ এ ব্যাকগ্রাউন্ড থেকে অ্যাক্টিভিটি খোলা ব্লক। নোটিফিকেশনে "খুলুন" দিতে হবে |
| **Camera** | Host ব্যবহারকারী নিজে চালু করে, দৃশ্যমান ইন্ডিকেটর | দূর থেকে গোপনে চালু করা যাবে না (এবং করা উচিতও নয়) |
| **Revoke / Pause / Disconnect All** | Host যেকোনো সময় বন্ধ করতে পারে, সঙ্গে সঙ্গে সব session শেষ | বর্তমান কোডে নেই, নতুন বানাতে হবে |

## ৪. Screen share ও Camera-র জন্য সঠিক প্রযুক্তি

JPEG ফ্রেম সার্ভারের মাধ্যমে পাঠালে Internet-এ ধীর হবে আর বেশি ব্যান্ডউইথ লাগবে। বাস্তবে এর জন্য **WebRTC** ব্যবহার করুন:

- সার্ভার শুধু signaling (সংযোগ ঠিক করার বার্তা) করে।
- ভিডিও সরাসরি ফোনে ফোনে যায়, STUN দিয়ে। সরাসরি না হলে TURN সার্ভার দিয়ে ঘুরে যায়।
- ফাইল ও command একই WebRTC data channel দিয়ে বা আলাদা TLS socket দিয়ে যেতে পারে।

LAN-এ আপাতত সাধারণ TLS stream দিয়েও চলে। Internet-এর জন্য WebRTC-ই ঠিক পথ।

## ৫. যা বানানো লাগবে (অগ্রাধিকার অনুযায়ী)

1. **নিরাপত্তা গেট:** token ছাড়া কোনো command চলবে না, Host Allow না দেওয়া পর্যন্ত কিছু নয়। এটাই এখন সবচেয়ে জরুরি।
2. **mutual TLS + পিন করা certificate**, একটা স্থায়ী সংযোগ (প্রতি command-এ নতুন socket নয়)।
3. **Permission ডিফল্ট বন্ধ**, Host চালু করলে তবেই কাজ করবে, সার্ভার নিজে enforce করবে।
4. **ভুয়া জিনিস সরানো:** নকল AI reply, "TLS Encrypted" লেখা, নকল key।
5. **আসল ফিচার:** Remote chat → ফাইল পাঠানো → Screen share (Controller-এ দেখা যায় এমনভাবে) → Gallery → Revoke/Pause।
6. **Internet relay:** সার্ভারে account ও device যাচাই, pair তালিকা, rate limit, revoke; ফোনে WSS client।
7. **WebRTC** screen/camera-র জন্য।
8. **Diagnostics স্ক্রিন:** কেন সংযোগ হচ্ছে না (client isolation, ভুল network, relay বন্ধ) পরিষ্কার বলে দেওয়া।

## ৬. আপনাকে নিজে যা করতে হবে (AI Studio পারবে না)

- **সার্ভার deploy:** domain, HTTPS সার্টিফিকেট, ডেটাবেস, মনিটরিং। এটা ছাড়া Internet mode চলবে না।
- **দুটি আসল ফোনে পরীক্ষা:** একই Wi‑Fi-তে, আলাদা নেটওয়ার্কে, মোবাইল ডেটায়, রাউটারের guest Wi‑Fi-তে।
- **ফোন কোম্পানির ব্যাটারি সীমা:** Xiaomi, Samsung ইত্যাদিতে ব্যাকগ্রাউন্ড service মেরে ফেলতে পারে। অ্যাপকে battery optimization থেকে বাদ দিতে হবে।
- **Play Store নীতি:** Accessibility ও screen share ব্যবহারে নীতি কঠোর। অ্যাপ প্রকাশ করতে চাইলে আগে নীতি দেখে নিন।

## ৭. পরীক্ষায় যা দেখে বুঝবেন সত্যিই কাজ করছে

- Host Allow না দিলে Controller কিছুই করতে পারে না।
- একই Wi‑Fi-র তৃতীয় ফোন থেকে সরাসরি command পাঠালে TLS-এই প্রত্যাখ্যাত হয়।
- Revoke করলে সঙ্গে সঙ্গে সংযোগ কাটে।
- Wi‑Fi বন্ধ করলে ঠিকঠাক "Offline" দেখায়।
- Screen share-এ Controller-এ আসল ছবি আসে, Host-এ Stop কাজ করে।
- ফাইল পাঠানোর পর হ্যাশ মেলে, মাঝপথে বাতিল করলে অর্ধেক ফাইল মুছে যায়।

## ৮. সম্পর্কিত ফাইল

- `JARVIS_FULL_FIX_UPGRADE_Master_Prompt_BN.md`: NEXUS ও Chat ঠিক করার সম্পূর্ণ ধাপ, AI Studio-র জন্য। এতে WebRTC ও Remote touch এখনো যোগ করা হয়নি।
- `JARVIS_CHAT_SYSTEM_Fix_Master_Prompt_BN.md`: শুধু Chat সিস্টেমের জন্য।
