/* Begin, prompt: Provide a simple utility class in Kotlin to encrypt and decrypt message text */
package com.example.whichzup.chat.utils

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    /**
     * Encrypts plaintext. Returns a Base64 string containing both the IV and the Ciphertext.
     */
    fun encrypt(plainText: String, secretKey: SecretKey): String {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)

            // Generate a random initialization vector (IV) for this encryption
            val iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Prepend IV to ciphertext for use during decryption
            val combined = iv + cipherText
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            return "" // Handle properly in production with custom exceptions/Result wrappers
        }
    }

    /**
     * Decrypts a Base64 string containing the IV and Ciphertext.
     */
    fun decrypt(encryptedText: String, secretKey: SecretKey): String {
        try {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)

            // Extract the IV from the beginning
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
            val cipherText = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val plainText = cipher.doFinal(cipherText)

            return String(plainText, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return "" // Handle properly in production
        }
    }
}
/* End */