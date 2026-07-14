package com.example.potty

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Implements Envelope Encryption for the SQLCipher database.
 * 
 * 1. Generates a cryptographically strong random passphrase (the Data Encryption Key - DEK).
 * 2. Uses a hardware-backed Master Key (KEK) from Android Keystore to encrypt/decrypt the DEK.
 * 3. Stores the encrypted DEK in SharedPreferences.
 */
object DatabaseKeyManager {
    private const val KEY_ALIAS = "potty_master_kek"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "secure_db_prefs"
    private const val ENCRYPTED_PASSPHRASE_KEY = "encrypted_dek"
    private const val IV_KEY = "dek_iv"
    
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val PASSPHRASE_LENGTH = 64

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedDekBase64 = prefs.getString(ENCRYPTED_PASSPHRASE_KEY, null)
        val ivBase64 = prefs.getString(IV_KEY, null)

        return if (encryptedDekBase64 != null && ivBase64 != null) {
            // Decrypt existing passphrase
            decryptPassphrase(encryptedDekBase64, ivBase64)
        } else {
            // Generate, encrypt, and store new passphrase
            val newPassphrase = generateRandomPassphrase()
            val encryptionResult = encryptPassphrase(newPassphrase)
            
            prefs.edit()
                .putString(ENCRYPTED_PASSPHRASE_KEY, encryptionResult.encryptedDek)
                .putString(IV_KEY, encryptionResult.iv)
                .apply()
            
            newPassphrase
        }
    }

    private fun generateRandomPassphrase(): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(PASSPHRASE_LENGTH)
        random.nextBytes(bytes)
        // Convert to Base64 to get a printable string passphrase for SQLCipher
        return Base64.encode(bytes, Base64.NO_WRAP)
    }

    private fun getOrGenerateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Ensure key is backed by hardware if available
                .setUserAuthenticationRequired(false) 
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptPassphrase(passphrase: ByteArray): EncryptionResult {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrGenerateMasterKey())
        
        val encryptedBytes = cipher.doFinal(passphrase)
        val iv = cipher.iv
        
        return EncryptionResult(
            encryptedDek = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    private fun decryptPassphrase(encryptedDekBase64: String, ivBase64: String): ByteArray {
        val encryptedBytes = Base64.decode(encryptedDekBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrGenerateMasterKey(), spec)
        
        return cipher.doFinal(encryptedBytes)
    }

    private data class EncryptionResult(val encryptedDek: String, val iv: String)
}
