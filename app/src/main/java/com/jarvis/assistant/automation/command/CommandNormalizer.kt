package com.jarvis.assistant.automation.command

import java.util.Locale

/**
 * CommandNormalizer provides intelligent mapping for Bengali (বাংলা) and Banglish
 * voice and text commands into standardized command phrases that Jarvis can execute.
 *
 * Examples:
 * - "ইউটিউব খোলো" / "youtube kholo" -> "open youtube"
 * - "হোয়াটসঅ্যাপ খোলো" / "whatsapp kholo" -> "open whatsapp"
 * - "টর্চ জ্বালাও" / "torch jalao" -> "turn on flashlight"
 * - "টর্চ বন্ধ করো" / "torch bondho koro" -> "turn off flashlight"
 * - "গান বাজাও [গান]" / "gaan bajao [song]" -> "play [song]"
 * - "ফোন করো [নাম]" / "phone koro [name]" -> "call [name]"
 * - "আবহাওয়া কেমন" / "weather kemon" -> "weather"
 * - "ব্যাটারি কত" / "battery koto" -> "battery"
 */
object CommandNormalizer {

    fun normalize(input: String): String {
        var text = input.trim()
        if (text.isBlank()) return text

        // Remove Jarvis wake prefix in Bengali or English
        text = text.replace(Regex("(?i)^(জার্ভিস|hey jarvis|hi jarvis|jarvis)[,\\s]*"), "").trim()

        val lower = text.lowercase(Locale.ROOT)

        // 0. Phone Lock & Unlock in Bangla & Banglish
        if (lower.matches(Regex(".*(?:ফোন|মোবাইল|device|phone|mobile)\\s*(?:লক\\s*করো|লক\\s*কর|লক\\s*করে\\s*দাও|লক|lock\\s*koro|lock).*")) ||
            lower.matches(Regex("^(?:lock\\s*my\\s*phone|lock\\s*phone|phone\\s*lock|ফোন\\s*লক)$"))) {
            return "lock my phone"
        }
        if (lower.matches(Regex(".*(?:আনলক\\s*স্ক্রিন|লক\\s*স্ক্রিন|unlock\\s*screen|lock\\s*screen)\\s*(?:দেখাও|খুলো|খোলো|dekhao|kholo|show).*")) ||
            lower.matches(Regex("^(?:show\\s*unlock\\s*screen|unlock\\s*screen|আনলক\\s*স্ক্রিন\\s*দেখাও|লক\\s*স্ক্রিন\\s*দেখাও)$"))) {
            return "show unlock screen"
        }

        // 1. Flashlight / Torch in Bangla & Banglish
        if (lower.matches(Regex(".*(টর্চ|ফ্ল্যাশলাইট|লাইট|batti|torch|flashlight)\\s*(জ্বালাও|চালাও|অন করো|চালু করো|on\\s*koro|chalu\\s*koro|jalao|on).*"))) {
            return "turn on flashlight"
        }
        if (lower.matches(Regex(".*(টর্চ|ফ্ল্যাশলাইট|লাইট|batti|torch|flashlight)\\s*(বন্ধ করো|নিভাও|অফ করো|bondho\\s*koro|bujhao|off\\s*koro|nibhao|off).*"))) {
            return "turn off flashlight"
        }

        // 2. YouTube Play / Gaan Bajao in Bangla & Banglish
        val playBanglaMatch = Regex("^(?:গান\\s*বাজাও|ভিডিও\\s*চালাও|বাজাও|গান\\s*শোনাও)\\s+(.+)$", RegexOption.IGNORE_CASE).find(lower)
        if (playBanglaMatch != null) {
            val query = playBanglaMatch.groupValues[1].trim()
            return "play $query"
        }
        val playBanglishMatch = Regex("^(?:gaan\\s*bajao|video\\s*chalao|bajao|gana\\s*chalao)\\s+(.+)$", RegexOption.IGNORE_CASE).find(lower)
        if (playBanglishMatch != null) {
            val query = playBanglishMatch.groupValues[1].trim()
            return "play $query"
        }

        // 3. Open YouTube in Bangla & Banglish
        if (lower.matches(Regex(".*(?:ইউটিউব|youtube)\\s*(?:খোলো|চালু করো|খোল|kholo|open).*")) ||
            lower.matches(Regex(".*(?:খোলো|চালু করো|kholo|open)\\s*(?:ইউটিউব|youtube).*"))) {
            return "open youtube"
        }

        // 4. Open WhatsApp in Bangla & Banglish
        if (lower.matches(Regex(".*(?:হোয়াটসঅ্যাপ|whatsapp)\\s*(?:খোলো|চালু করো|খোল|kholo|open).*")) ||
            lower.matches(Regex(".*(?:খোলো|চালু করো|kholo|open)\\s*(?:হোয়াটসঅ্যাপ|whatsapp).*"))) {
            return "open whatsapp"
        }

        // 5. Phone Call in Bangla & Banglish
        val callBanglaMatch = Regex("^(?:ফোন\\s*করো|কল\\s*করো|কল\\ দাও)\\s+(.+)$", RegexOption.IGNORE_CASE).find(lower)
        if (callBanglaMatch != null) {
            val contact = callBanglaMatch.groupValues[1].trim()
            return "call $contact"
        }
        val callBanglishMatch = Regex("^(?:phone\\s*koro|call\\s*koro|call\\s*dao)\\s+(.+)$", RegexOption.IGNORE_CASE).find(lower)
        if (callBanglishMatch != null) {
            val contact = callBanglishMatch.groupValues[1].trim()
            return "call $contact"
        }

        // 6. Weather Query in Bangla & Banglish
        if (lower.contains("আবহাওয়া") || lower.contains("weather kemon") || lower.contains("ajker weather") || lower.contains("বৃষ্টি হবে কি")) {
            return "weather"
        }

        // 7. Battery Status
        if (lower.contains("ব্যাটারি কত") || lower.contains("battery koto") || lower.contains("battery charge")) {
            return "battery status"
        }

        // 8. General App Open (যেমন "ফেসবুক খোলো", "ক্যামেরা খোলো", "camera kholo")
        val openAppBanglaMatch = Regex("^(.+?)\\s*(?:অ্যাপ\\s*)?(?:খোলো|চালু করো|খোল)$", RegexOption.IGNORE_CASE).find(lower)
        if (openAppBanglaMatch != null) {
            val appName = openAppBanglaMatch.groupValues[1].trim()
            val translatedApp = translateCommonAppName(appName)
            return "open $translatedApp"
        }
        val openAppBanglishMatch = Regex("^(.+?)\\s*(?:app\\s*)?(?:kholo|open)$", RegexOption.IGNORE_CASE).find(lower)
        if (openAppBanglishMatch != null) {
            val appName = openAppBanglishMatch.groupValues[1].trim()
            return "open $appName"
        }

        // 9. Confirmations in Bangla
        if (lower.matches(Regex("^(হ্যাঁ|হ্যা|ঠিক আছে|হাঁ|অবশ্যই|পাঠাও|পাঠিয়ে দাও|হুম|ha|haan|thik ache|thik ase|pathao)$"))) {
            return "confirm"
        }
        if (lower.matches(Regex("^(না|থাক|বাতিল করো|বন্ধ রাখো|থেমে যাও|na|thak|cancel|bondho|ruko)$"))) {
            return "cancel"
        }

        return text
    }

    private fun translateCommonAppName(name: String): String {
        return when (name.lowercase(Locale.ROOT)) {
            "ক্যামেরা" -> "camera"
            "গ্যালারি" -> "gallery"
            "ক্রোম", "ব্রাউজার" -> "chrome"
            "ইউটিউব" -> "youtube"
            "হোয়াটসঅ্যাপ" -> "whatsapp"
            "ফেসবুক" -> "facebook"
            "মেসেঞ্জার" -> "messenger"
            "সেটিংস" -> "settings"
            "ক্যালকুলেটর" -> "calculator"
            "ঘড়ি", "অ্যালার্ম" -> "clock"
            "নোট", "নোটস" -> "notes"
            else -> name
        }
    }
}
