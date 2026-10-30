# JARVIS Phone Lock & Unlock — সম্পূর্ণ বাস্তবায়ন গাইড
**ভাষা:** বাংলা  
**প্রজেক্ট:** বিদ্যমান Kotlin/Android JARVIS অ্যাপ  
**লক্ষ্য:** নিজের Android ফোনে ভয়েস কমান্ড দিয়ে ফোন লক করা, স্ক্রিন জাগানোর চেষ্টা করা এবং Android-এর আসল Pattern/PIN/Password/Fingerprint unlock flow দেখানো।

---

## ১. আগে বুঝে নাও: কী সম্ভব, কী সম্ভব নয়

### সম্ভব
- “Jarvis, lock my phone” / “ফোন লক করো” কমান্ডে ফোন লক করা।
- “স্ক্রিন জাগাও” কমান্ডে সমর্থিত Android পরিস্থিতিতে স্ক্রিন জাগানোর অনুরোধ।
- “আনলক স্ক্রিন দেখাও” কমান্ডে Android-এর আসল Keyguard/লক স্ক্রিন দেখানো বা সিস্টেমকে dismiss flow অনুরোধ করা।
- ব্যবহারকারী নিজে Pattern, PIN, Password বা ফোনের সমর্থিত biometric পদ্ধতিতে আনলক করবেন।
- বাংলা/ইংরেজি ভয়েস কমান্ড, permission status, action history, offline command parsing এবং error message যোগ করা।

### সাধারণ Android অ্যাপ দিয়ে সম্ভব নয়
- Pattern/PIN/Password না জেনে নিরাপদ লক বাইপাস করা।
- ফোনের সিস্টেম Pattern বা PIN পড়া/সংরক্ষণ করা।
- `KeyguardManager` দিয়ে যেকোনো ফোনকে জোর করে আনলক করা। Secure lock থাকলে Android নিজেই credentials চাইতে পারে।
- সাধারণ অ্যাপ হিসেবে সব ফোনে background থেকে স্ক্রিন জাগানো বা Activity খোলার নিশ্চয়তা দেওয়া।
- Android-এর আসল lock screen-এ স্বয়ংক্রিয়ভাবে Pattern আঁকা বা PIN প্রবেশ করানো।

**নিরাপত্তার নিয়ম:** JARVIS-এ কখনো PIN/Pattern সংগ্রহ, লগ, upload বা store করা যাবে না। ফোনের সিস্টেম authentication-ই কর্তৃত্বপূর্ণ থাকবে।

---

## ২. ফিচারের কার্যপ্রবাহ

### A. Phone Lock
1. Vosk/বর্তমান speech engine কমান্ডকে text-এ রূপান্তর করবে।
2. Command parser text-টি `LOCK_PHONE` action হিসেবে শনাক্ত করবে।
3. JARVIS পরীক্ষা করবে Device Admin সক্রিয় আছে কি না।
4. সক্রিয় থাকলে `DevicePolicyManager.lockNow()` কল করবে।
5. অনুমতি না থাকলে Android-এর Device Admin activation screen দেখানোর নির্দেশনা দেবে।
6. ফলাফল UI/voice response-এ জানাবে; API কল করলেই lock সফল হয়েছে বলে অযাচাইকৃত দাবি করবে না।

### B. Show Unlock Screen
1. ব্যবহারকারী “show unlock screen” বলবেন।
2. JARVIS Activity দৃশ্যমান অবস্থায় Android `KeyguardManager` flow ব্যবহার করবে।
3. Android Pattern/PIN/Password/Fingerprint বা নিজের নির্ধারিত authentication UI দেখাবে।
4. ব্যবহারকারী নিজে authentication সম্পন্ন করবেন।
5. JARVIS credentials দেখবে না।

### C. Wake Screen
- দৃশ্যমান Activity থেকে `setTurnScreenOn(true)` / `setShowWhenLocked(true)` ব্যবহার করা যেতে পারে।
- এই API Activity-এর lifecycle/visibility-র জন্য; এটি একা যেকোনো সময়ে ফোনকে জাগানোর সার্বজনীন অনুমতি নয়।
- Background থেকে screen wake দরকার হলে Android version ও device policy অনুযায়ী foreground notification/ব্যবহারকারীর tap-ভিত্তিক flow তৈরি করতে হবে।

---

## ৩. প্রয়োজনীয় Android API ও permission

সব permission একসঙ্গে যোগ কোরো না। প্রয়োজন অনুযায়ী যোগ করবে।

| API / Permission | প্রয়োজন | গুরুত্বপূর্ণ কথা |
|---|---|---|
| `DevicePolicyManager` | ফোন লক | `lockNow()`-এর জন্য সক্রিয় Device Admin/প্রযোজ্য policy দরকার |
| `DeviceAdminReceiver` | Device Admin integration | Android system event গ্রহণ করে |
| `android.permission.BIND_DEVICE_ADMIN` | Manifest receiver-এ | এটি receiver-এ `android:permission` হিসেবে ব্যবহৃত হয়; runtime permission নয় |
| `res/xml/...device_admin.xml`-এ `<force-lock />` | `lockNow()` policy | ব্যবহারকারীকে system UI দিয়ে Device Admin সক্রিয় করতে হবে |
| `KeyguardManager` | লক স্ক্রিনের অবস্থা/সিস্টেম dismissal flow | Pattern/PIN bypass করে না |
| `RECORD_AUDIO` | ভয়েস কমান্ড | Android runtime permission লাগবে |
| `FOREGROUND_SERVICE` | প্রয়োজন হলে foreground service | Android সংস্করণ ও service type অনুযায়ী অতিরিক্ত শর্ত/permission লাগতে পারে |
| `POST_NOTIFICATIONS` | Android 13+ notification | notification দেখাতে runtime permission চাইতে হতে পারে |
| `WAKE_LOCK` | CPU জাগিয়ে রাখার নির্দিষ্ট কাজ | স্ক্রিন জাগানোর নিশ্চয়তা দেয় না; অকারণে ব্যবহার কোরো না |
| `setShowWhenLocked()` | Activity-কে lock screen-এর ওপর দেখানো | secure lock bypass নয় |
| `setTurnScreenOn()` | Activity চালুর সময় স্ক্রিন জাগানোর অনুরোধ | Activity lifecycle-এ ব্যবহার হয় |

### যেগুলো সাধারণত এই ফিচারের জন্য প্রয়োজন নেই
- `SYSTEM_ALERT_WINDOW`: শুধু lock screen দেখানোর জন্য এটি যোগ কোরো না।
- Accessibility Service: শুধু ফোন লক/আসল unlock screen দেখাতে প্রয়োজন নেই; অপ্রয়োজনীয় Accessibility permission এড়িয়ে চলো।
- Root permission: এই বাস্তবায়নের জন্য প্রয়োজন নেই।
- `USE_BIOMETRIC`: JARVIS-এর নিজের in-app biometric prompt চাইলে আলাদা প্রয়োজন হতে পারে; এটি ফোনের system lock bypass করে না।
- PIN/Pattern সংরক্ষণের permission বলে কিছু নেই—এমন কাজই করা উচিত নয়।

---

## ৪. প্রস্তাবিত ফাইল কাঠামো

বিদ্যমান project package name ও folder structure আগে পরীক্ষা করবে। নিচের নামগুলো উদাহরণ; project-এর আসল package path অনুযায়ী মানিয়ে নেবে।

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/jarvis/assistant/
│   ├── security/
│   │   ├── JarvisDeviceAdminReceiver.kt
│   │   └── PhoneLockController.kt
│   ├── phone/
│   │   └── UnlockPromptActivity.kt
│   └── voice/
│       └── PhoneCommandHandler.kt
└── res/xml/
    └── jarvis_device_admin.xml
```

বিদ্যমান JARVIS-এ যদি command parser, executor বা voice service থাকে, সেগুলো পুনরায় তৈরি না করে সেখানেই নতুন action যোগ করবে।

---

## ৫. ধাপে ধাপে বাস্তবায়ন

### Step 0 — Backup ও project inspection
1. মূল ZIP-এর একটি backup রাখো।
2. Android Studio-তে project খুলে Gradle Sync করো।
3. `app/build.gradle` বা `app/build.gradle.kts` থেকে `minSdk`, `targetSdk` ও compile SDK দেখো।
4. `AndroidManifest.xml`-এর package/namespace এবং বর্তমান Activity/voice service-এর নাম দেখো।
5. আগে থেকেই Device Admin receiver বা phone-control class থাকলে duplicate তৈরি কোরো না।
6. পরিবর্তনের আগে Git commit বা আলাদা backup copy রাখো।

### Step 1 — Device Admin XML তৈরি করো

ফাইল: `app/src/main/res/xml/jarvis_device_admin.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

`res/xml` folder না থাকলে তৈরি করো।

### Step 2 — Device Admin receiver তৈরি করো

ফাইল: `JarvisDeviceAdminReceiver.kt`

```kotlin
package com.jarvis.assistant.security

import android.app.admin.DeviceAdminReceiver

class JarvisDeviceAdminReceiver : DeviceAdminReceiver()
```

**গুরুত্বপূর্ণ:** তোমার প্রকৃত package name আলাদা হলে `package` line বদলাবে। Kotlin file-এর path-ও package-এর সঙ্গে মিলতে হবে।

### Step 3 — Manifest-এ receiver নিবন্ধন করো

`<application>`-এর ভেতরে যোগ করো:

```xml
<receiver
    android:name=".security.JarvisDeviceAdminReceiver"
    android:label="JARVIS Device Admin"
    android:description="@string/device_admin_description"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">

    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/jarvis_device_admin" />

    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

`res/values/strings.xml`-এ resource না থাকলে যোগ করো:

```xml
<string name="device_admin_description">
    JARVIS-এর Voice Lock ফিচার চালু করতে ফোন লক করার অনুমতি প্রয়োজন।
</string>
```

`BIND_DEVICE_ADMIN`-কে `<uses-permission>` হিসেবে যোগ করে কাজ শেষ হয়েছে ধরে নিও না। এখানে receiver-এর `android:permission` এবং system activation flow-ই গুরুত্বপূর্ণ।

### Step 4 — Device Admin activation screen খোলার function

এটি কোনো Activity থেকে চালাও:

```kotlin
package com.jarvis.assistant.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

fun createDeviceAdminIntent(context: Context): Intent {
    val component = ComponentName(
        context,
        JarvisDeviceAdminReceiver::class.java
    )

    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            component
        )
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "JARVIS Voice Lock চালু করতে Device Admin সক্রিয় করুন।"
        )
    }
}
```

Activity-তে `startActivity(createDeviceAdminIntent(this))` ব্যবহার করো। ব্যবহারকারী নিজে system screen-এ অনুমতি দেবেন। জোর করে enable করা যাবে না।

### Step 5 — PhoneLockController তৈরি করো

```kotlin
package com.jarvis.assistant.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

class PhoneLockController(context: Context) {
    private val appContext = context.applicationContext

    private val dpm = appContext.getSystemService(
        Context.DEVICE_POLICY_SERVICE
    ) as DevicePolicyManager

    private val admin = ComponentName(
        appContext,
        JarvisDeviceAdminReceiver::class.java
    )

    fun isAdminEnabled(): Boolean = dpm.isAdminActive(admin)

    fun lockPhone(): Result<Unit> {
        if (!isAdminEnabled()) {
            return Result.failure(
                IllegalStateException("DEVICE_ADMIN_NOT_ENABLED")
            )
        }

        return try {
            // Android 11+ এও সাধারণত active admin-এ
            // force-lock policy থাকা প্রয়োজন।
            dpm.lockNow()
            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e("PhoneLockController", "Lock permission denied", e)
            Result.failure(e)
        } catch (e: RuntimeException) {
            Log.e("PhoneLockController", "Lock request failed", e)
            Result.failure(e)
        }
    }
}
```

`Result.success` মানে API call exception ছাড়াই শেষ হয়েছে; এটি independent proof নয় যে display ইতিমধ্যে lock screen দেখিয়েছে। UI-তে “Lock command sent” বলো, “নিশ্চিতভাবে লক হয়েছে” নয়—যতক্ষণ না আলাদাভাবে যাচাই করেছ।

### Step 6 — Android-এর আসল unlock screen দেখানোর flow

সবচেয়ে নির্ভরযোগ্য প্রাথমিক UX হলো:
- স্ক্রিন জাগানোর জন্য ব্যবহারকারীকে notification/visible Activity থেকে JARVIS খুলতে দেওয়া;
- Activity সামনে এলে `KeyguardManager`-এর dismissal request ব্যবহার করা;
- Android-এর secure lock screen নিজেই Pattern/PIN/Password চাইবে।

Activity উদাহরণ:

```kotlin
package com.jarvis.assistant.phone

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class UnlockPromptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        // Android system authentication/dismissal completed.
                        finish()
                    }

                    override fun onDismissCancelled() {
                        // User cancelled; leave the system lock screen in control.
                    }

                    override fun onDismissError() {
                        // Show a safe message; never claim the phone was unlocked.
                    }
                }
            )
        } else {
            // পুরোনো Android: নিজের Activity দিয়ে lock bypass করার চেষ্টা নয়।
            // ব্যবহারকারীকে system lock screen-এ থাকতে দাও।
        }
    }
}
```

এটি AppCompat dependency থাকা project-এর জন্য উদাহরণ। project যদি `ComponentActivity`/অন্য base Activity ব্যবহার করে, সেই অনুযায়ী adjust করো। `requestDismissKeyguard()` সব সময় lock screen সরাবে না; secure credential থাকলে Android authentication চাইবে। এই callback-ও শুধু Android-এর রিপোর্ট করা ফলাফল।

### Step 7 — Manifest-এ Unlock Activity যোগ করো

`<application>`-এর ভেতরে:

```xml
<activity
    android:name=".phone.UnlockPromptActivity"
    android:exported="false"
    android:showWhenLocked="true"
    android:turnScreenOn="true" />
```

`showWhenLocked` ও `turnScreenOn` attribute Android-এর উপযুক্ত API level-এর সঙ্গে মিলিয়ে ব্যবহার করবে। `exported="false"` রাখলে অন্য অ্যাপ সরাসরি এই Activity চালু করতে পারবে না। এটি Activity-কে সব background পরিস্থিতিতে launch করার অনুমতি দেয় না।

### Step 8 — Voice command যুক্ত করো

বর্তমান voice engine থেকে পাওয়া text-কে আগে normalize করো; তারপর নির্দিষ্ট phrase-কে action-এ map করো। Speech recognition-কে নিজে নিজে arbitrary system action চালাতে দিও না।

উদাহরণ:

```kotlin
enum class PhoneAction {
    LOCK_PHONE,
    SHOW_UNLOCK_SCREEN,
    UNKNOWN
}

fun parsePhoneAction(raw: String): PhoneAction {
    val text = raw.trim().lowercase()

    return when (text) {
        "jarvis lock my phone",
        "lock my phone",
        "ফোন লক করো",
        "ফোন লক কর" -> PhoneAction.LOCK_PHONE

        "show unlock screen",
        "jarvis unlock screen",
        "আনলক স্ক্রিন দেখাও",
        "লক স্ক্রিন দেখাও" -> PhoneAction.SHOW_UNLOCK_SCREEN

        else -> PhoneAction.UNKNOWN
    }
}
```

এই parser কেবল action শনাক্ত করে। বিদ্যমান `CommandExecutor`-এ action পাঠিয়ে:
- `LOCK_PHONE` হলে `PhoneLockController.lockPhone()` চালাও;
- admin enabled না থাকলে activation screen-এর জন্য button/visible flow দেখাও;
- `SHOW_UNLOCK_SCREEN` হলে Activity foreground-এ থাকলে unlock flow খোলো;
- background থেকে launch অনুমোদিত না হলে notification-এ “Open JARVIS / Show unlock screen” action দেখাও;
- `UNKNOWN` হলে কোনো system action চালিও না।

**নোট:** `startActivity()` দিয়ে background থেকে Activity খুলতে Android সংস্করণভেদে সীমাবদ্ধতা আছে। Android 10+ এ সাধারণভাবে background activity launch-এর ওপর বিধিনিষেধ আছে। তাই notification বা ব্যবহারকারীর স্পষ্ট interaction-কে fallback হিসেবে রাখো।

### Step 9 — Voice engine ও background service

যদি JARVIS-এ ইতিমধ্যে Vosk বা foreground voice service থাকে, তার lifecycle-এর সঙ্গে command parser যুক্ত করো। দ্বিতীয় microphone loop/দ্বিতীয় service অকারণে চালু কোরো না।

- `RECORD_AUDIO` Manifest-এ ঘোষণা করো এবং runtime permission চাও।
- Android 13+ এ notification দেখানোর জন্য `POST_NOTIFICATIONS` runtime permission flow বিবেচনা করো।
- Foreground service প্রয়োজন হলে `FOREGROUND_SERVICE` এবং target Android version অনুযায়ী service-type-specific permission/manifest declarations ঠিক করো।
- Microphone foreground service-কে background থেকে যেকোনো সময় শুরু করা যাবে ধরে নিও না।
- ব্যবহারকারীকে স্পষ্ট notification এবং Stop action দাও।
- Wake word সব ফোনে নিরবচ্ছিন্নভাবে কাজ করবে বলে প্রতিশ্রুতি দিও না।

---

## ৬. Settings UI-তে কী থাকবে

একটি “Phone Control” screen তৈরি করো:

1. **Device Admin Status:** Enabled / Not enabled
2. **Enable Device Admin:** system activation screen খোলে
3. **Test Lock:** button চাপলে ফোন লক করার অনুরোধ
4. **Show Unlock Screen:** visible interaction থেকে system unlock flow
5. **Microphone Permission:** Granted / Denied
6. **Voice Commands:** বাংলা/ইংরেজি phrase-এর তালিকা
7. **Action History:** শুধু command type, timestamp ও result; কোনো PIN/Pattern নয়
8. **Stop Voice Listening:** listening service বন্ধ
9. **Privacy Note:** JARVIS system credentials সংগ্রহ করে না—এটি স্পষ্টভাবে দেখানো

---

## ৭. Test plan — বাস্তব ফোনে পরীক্ষা

### Build ও configuration
- [ ] ZIP-এর backup আছে।
- [ ] Package/namespace সঠিক।
- [ ] Gradle Sync সফল।
- [ ] Manifest merge error নেই।
- [ ] Device Admin XML resource পাওয়া যাচ্ছে।
- [ ] App install ও launch হচ্ছে।

### Device Admin / Lock
- [ ] Device Admin বন্ধ থাকলে lock command সঠিক নির্দেশনা দেয়।
- [ ] ব্যবহারকারী Device Admin system UI-তে নিজে enable করতে পারেন।
- [ ] “ফোন লক করো” দিলে lock request যায়।
- [ ] Admin disable করলে অ্যাপ false success দেখায় না।
- [ ] SecurityException হলে error state দেখায়।

### Unlock screen
- [ ] JARVIS foreground-এ থাকলে unlock screen request পরীক্ষা করা হয়েছে।
- [ ] PIN lock-এ Android PIN screen দেখায়।
- [ ] Pattern lock-এ Android Pattern screen দেখায়।
- [ ] Password lock-এ Android Password screen দেখায়।
- [ ] Fingerprint/biometric-এর নিয়ম Android নিজেই পরিচালনা করে।
- [ ] Cancel/error হলে JARVIS “Unlocked” বলে না।
- [ ] Background launch blocked হলে notification fallback আছে।

### Voice / Privacy
- [ ] বাংলা ও ইংরেজি command কাজ করে।
- [ ] ভুল কমান্ডে lock/unlock action চলে না।
- [ ] Microphone permission denied হলে পরিষ্কার বার্তা আসে।
- [ ] PIN/Pattern কোনো log, database, analytics বা server-এ যায় না।
- [ ] Stop command voice service বন্ধ করে।
- [ ] ফোন restart-এর পর permission status পুনরায় পরীক্ষা হয়।

### Compatibility
- [ ] অন্তত একটি বাস্তব ফোনে পরীক্ষা।
- [ ] Android version, device model, targetSdk এবং app lifecycle নথিভুক্ত।
- [ ] Battery optimization/Doze-এ voice service-এর আচরণ পরীক্ষা।
- [ ] Android version বদলালে manifest/service rules পুনরায় যাচাই।

---

## ৮. সাধারণ সমস্যা ও সমাধান

### “Device admin not enabled”
Settings screen খোলার জন্য button দেখাও। ব্যবহারকারী নিজে Device Admin enable না করা পর্যন্ত lock action চালিও না।

### `SecurityException`
Receiver declaration, active admin status, `<force-lock />` policy এবং Android version যাচাই করো।

### “Unlock screen opens only when app is foreground”
এটি Android-এর background activity restriction হতে পারে। Notification action বা user tap fallback ব্যবহার করো।

### “Screen does not wake”
`WAKE_LOCK` যোগ করলেই সমাধান হবে না। `setTurnScreenOn()` Activity-এর ক্ষেত্রে প্রযোজ্য। Device model, screen state, power management ও lifecycle পরীক্ষা করো।

### Build error: resource not found
`res/xml/jarvis_device_admin.xml`, `res/values/strings.xml` এবং Manifest resource names মিলিয়ে দেখো।

### Duplicate receiver/activity
পুরোনো project-এ একই class বা receiver থাকলে দ্বিতীয়বার register কোরো না। আগে project search করো।

---

## ৯. বাস্তবায়নের সুপারিশকৃত ক্রম

1. **Phase 1:** Device Admin XML + Receiver + Lock Controller।
2. **Phase 2:** Settings screen থেকে manual Test Lock।
3. **Phase 3:** Existing voice parser/executor-এ lock command যোগ।
4. **Phase 4:** Foreground UnlockPromptActivity + Keyguard flow।
5. **Phase 5:** Background limitation-এর notification fallback।
6. **Phase 6:** Bengali/Banglish phrase, error handling ও privacy-safe history।
7. **Phase 7:** বাস্তব ফোনে test, Android version অনুযায়ী correction ও regression tests।

---

## ১০. চূড়ান্ত নোট

এই feature-এ Android system-এর security-ই শেষ সিদ্ধান্ত নেবে। JARVIS-এর দায়িত্ব হলো command চিনে অনুমোদিত system API অনুরোধ করা, ব্যবহারকারীকে প্রয়োজনীয় screen-এ নিয়ে যাওয়া এবং সঠিক ফলাফল জানানো। এটি Pattern/PIN bypass tool নয়।

Android version জানা না থাকলে প্রথমে ফোনের **Settings → About phone → Android version** দেখো। তারপর project-এর `minSdk`, `targetSdk` এবং Gradle build error অনুযায়ী implementation সামঞ্জস্য করো।
