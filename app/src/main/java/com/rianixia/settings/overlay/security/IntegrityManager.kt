package com.rianixia.settings.overlay.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
// Ensure this matches the 'namespace' in your app/build.gradle
import com.rianixia.settings.overlay.BuildConfig
import java.security.MessageDigest

object IntegrityManager {

    /**
     * Checks if the current app signature matches the expected signature defined in build.gradle.
     * Automatically switches between DEBUG and RELEASE hashes based on the active build variant.
     */
    fun isSafe(context: Context): Boolean {
        // Reads the hash injected by Gradle
        val expectedSignature = BuildConfig.ALLOWED_SIGNATURE_HASH
        val currentSignature = getAppSignature(context)

        // Safety check: If the user hasn't configured the Release hash yet, log it but don't crash loop.
        if (expectedSignature.contains("PLACEHOLDER_HASH")) {
            Log.e("IntegrityCheck", "SECURITY CONFIGURATION MISSING: Release hash not set in build.gradle")
            Log.e("IntegrityCheck", "Detected Release Signature: $currentSignature")
            return false // Fail secure: Refuse to run if hash is missing
        }

        val isMatch = currentSignature.equals(expectedSignature, ignoreCase = true)

        if (!isMatch) {
            Log.e("IntegrityCheck", "SECURITY VIOLATION")
            Log.e("IntegrityCheck", "Expected (Gradle): $expectedSignature")
            Log.e("IntegrityCheck", "Actual (Device):   $currentSignature")
        }

        return isMatch
    }

    private fun getAppSignature(context: Context): String {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }

            if (signatures.isNullOrEmpty()) return "ERROR_NO_SIG"

            // Hash the first signature found
            hashSignature(signatures[0].toByteArray())
        } catch (e: Exception) {
            Log.e("IntegrityCheck", "Error getting signature", e)
            "ERROR_EXCEPTION"
        }
    }

    private fun hashSignature(signature: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(signature)
            // Convert to Hex String (Lowercase)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "ERROR_HASHING"
        }
    }
}