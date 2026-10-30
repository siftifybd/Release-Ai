package com.jarvis.assistant.security

import com.jarvis.assistant.automation.command.CommandNormalizer
import com.jarvis.assistant.automation.command.CommandParser
import com.jarvis.assistant.automation.command.JarvisCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLockUnlockTest {

    @Test
    fun testBengaliLockNormalization() {
        val bengaliLock = CommandNormalizer.normalize("ফোন লক করো")
        assertEquals("lock my phone", bengaliLock)

        val banglishLock = CommandNormalizer.normalize("phone lock koro")
        assertEquals("lock my phone", banglishLock)
    }

    @Test
    fun testBengaliUnlockScreenNormalization() {
        val bengaliUnlock = CommandNormalizer.normalize("আনলক স্ক্রিন দেখাও")
        assertEquals("show unlock screen", bengaliUnlock)

        val banglishUnlock = CommandNormalizer.normalize("unlock screen dekhao")
        assertEquals("show unlock screen", banglishUnlock)
    }

    @Test
    fun testCommandParserLock() {
        val parsedEnglish = CommandParser.parse("lock my phone")
        assertTrue(parsedEnglish.command is JarvisCommand.LockPhone)

        val parsedBengali = CommandParser.parse("ফোন লক করো")
        assertTrue(parsedBengali.command is JarvisCommand.LockPhone)

        val parsedShort = CommandParser.parse("lock phone")
        assertTrue(parsedShort.command is JarvisCommand.LockPhone)
    }

    @Test
    fun testCommandParserUnlockScreen() {
        val parsedEnglish = CommandParser.parse("show unlock screen")
        assertTrue(parsedEnglish.command is JarvisCommand.ShowUnlockScreen)

        val parsedBengali = CommandParser.parse("আনলক স্ক্রিন দেখাও")
        assertTrue(parsedBengali.command is JarvisCommand.ShowUnlockScreen)
    }

    @Test
    fun testDeviceAdminPermissionHelperConstants() {
        assertEquals("JARVIS Voice Lock চালু করতে Device Admin সক্রিয় করুন।", DeviceAdminPermissionHelper.DEFAULT_EXPLANATION)
        assertEquals(1042, DeviceAdminPermissionHelper.REQUEST_CODE_DEVICE_ADMIN)
        assertEquals("DeviceAdminPermissionHelper", DeviceAdminPermissionHelper.TAG)
    }
}

