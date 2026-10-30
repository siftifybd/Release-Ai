package com.jarvis.assistant.nexus.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object NexusSecurityManager {

    private const val TAG = "NexusSecurityManager"
    private const val KEY_ALIAS = "jarvis_nexus_device_identity"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private val secureRandom = SecureRandom()

    // Dynamic pairing code on Host: code -> timestamp
    private val activePairingCodes = mutableMapOf<String, Long>()
    private const val CODE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

    // Current displayed Host pairing code
    @Volatile
    private var currentHostPairingCode: String = ""

    fun getOrCreateDeviceKeyPair(): Pair<PublicKey, PrivateKey>? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    ANDROID_KEYSTORE
                )
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .build()
                kpg.initialize(parameterSpec)
                kpg.generateKeyPair()
            }

            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                Pair(entry.certificate.publicKey, entry.privateKey)
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get or create Keystore key pair: ${e.message}")
            null
        }
    }

    fun getPublicKeyBase64(): String {
        return try {
            val keyPair = getOrCreateDeviceKeyPair()
            if (keyPair != null) {
                Base64.encodeToString(keyPair.first.encoded, Base64.NO_WRAP)
            } else {
                "NEXUS-PUBKEY-${android.os.Build.SERIAL.hashCode()}"
            }
        } catch (_: Throwable) {
            "NEXUS-PUBKEY-DEVICE"
        }
    }

    /**
     * Generates a dynamic single-use cryptographically random 6-digit pairing code with 5-min TTL.
     */
    @Synchronized
    fun generatePairingCode(): String {
        cleanExpiredCodes()
        val num = 100000 + secureRandom.nextInt(900000)
        val code = num.toString()
        activePairingCodes[code] = System.currentTimeMillis()
        currentHostPairingCode = code
        return code
    }

    @Synchronized
    fun getCurrentPairingCode(): String {
        if (currentHostPairingCode.isEmpty()) {
            return generatePairingCode()
        }
        val created = activePairingCodes[currentHostPairingCode] ?: 0L
        if (System.currentTimeMillis() - created > CODE_EXPIRY_MS) {
            return generatePairingCode()
        }
        return currentHostPairingCode
    }

    /**
     * Validates a 6-digit code against active codes with real-time expiration check.
     */
    @Synchronized
    fun verifyPairingCode(code: String): Boolean {
        cleanExpiredCodes()
        val trimmed = code.trim()
        val created = activePairingCodes[trimmed] ?: return false
        if (System.currentTimeMillis() - created <= CODE_EXPIRY_MS) {
            activePairingCodes.remove(trimmed) // single-use consume
            return true
        }
        activePairingCodes.remove(trimmed)
        return false
    }

    private fun cleanExpiredCodes() {
        val now = System.currentTimeMillis()
        val iter = activePairingCodes.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value > CODE_EXPIRY_MS) {
                iter.remove()
            }
        }
    }

    fun signData(data: String): String {
        return try {
            val keyPair = getOrCreateDeviceKeyPair() ?: return "SIG-FALLBACK"
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(keyPair.second)
            s.update(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(s.sign(), Base64.NO_WRAP)
        } catch (e: Throwable) {
            Log.e(TAG, "Signature failed: ${e.message}")
            "SIG-ERROR"
        }
    }

    fun encryptPayload(plainText: String, secretKeyBytes: ByteArray): Pair<String, String>? {
        return try {
            val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(secretKeyBytes.copyOf(32), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Pair(
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            null
        }
    }

    fun decryptPayload(cipherTextBase64: String, ivBase64: String, secretKeyBytes: ByteArray): String? {
        return try {
            val cipherText = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(secretKeyBytes.copyOf(32), "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }
}
