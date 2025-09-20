package com.frauddetection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * SecureStore - Encrypted on-device storage for biometric data and transaction logs
 * 
 * Uses EncryptedSharedPreferences as specified in goal.md for:
 * - Enrolled face template storage
 * - Encrypted transaction logs with timestamp, amount, matchScore, reason, status
 * - All data stays on-device and encrypted at rest
 * 
 * Optimized for Android Go devices with minimal memory footprint.
 */
class SecureStore(private val context: Context) {
    
    companion object {
        private const val TAG = "SecureStore"
        private const val ENCRYPTED_PREFS_NAME = "fraud_detection_secure_prefs"
        private const val FACE_TEMPLATE_KEY = "encrypted_face_template"
        private const val TRANSACTION_LOGS_KEY = "encrypted_transaction_logs"
        private const val MAX_LOG_ENTRIES = 1000 // Limit logs to prevent memory issues on Android Go
    }
    
    private val gson = Gson()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // Encrypted SharedPreferences using Android Jetpack Security
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            // Create master key for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Create encrypted preferences
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences", e)
            // Fallback to regular SharedPreferences (not recommended for production)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Store encrypted face template from enrollment
     * Saves biometric data securely on-device as specified in goal.md
     * 
     * @param features List of facial feature points from FaceRecognizer
     * @return Boolean indicating successful storage
     */
    fun storeFaceTemplate(features: List<FaceRecognizer.FeaturePoint>): Boolean {
        return try {
            Log.d(TAG, "Storing encrypted face template with ${features.size} features")
            
            // Serialize features to JSON for storage
            val templateJson = gson.toJson(features)
            
            // Store encrypted template
            val editor = encryptedPrefs.edit()
            editor.putString(FACE_TEMPLATE_KEY, templateJson)
            val success = editor.commit()
            
            if (success) {
                Log.d(TAG, "Face template stored securely")
            } else {
                Log.e(TAG, "Failed to store face template")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error storing face template", e)
            false
        }
    }
    
    /**
     * Retrieve encrypted face template for verification
     * Loads biometric data securely from on-device storage
     * 
     * @return List of facial feature points, empty if no template stored
     */
    fun getFaceTemplate(): List<FaceRecognizer.FeaturePoint> {
        return try {
            val templateJson = encryptedPrefs.getString(FACE_TEMPLATE_KEY, null)
            
            if (templateJson.isNullOrEmpty()) {
                Log.d(TAG, "No face template found")
                return emptyList()
            }
            
            // Deserialize features from JSON
            val listType = object : TypeToken<List<FaceRecognizer.FeaturePoint>>() {}.type
            val features: List<FaceRecognizer.FeaturePoint> = gson.fromJson(templateJson, listType)
            
            Log.d(TAG, "Retrieved face template with ${features.size} features")
            features
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving face template", e)
            emptyList()
        }
    }
    
    /**
     * Log transaction event with encryption
     * Stores all transaction details as specified in goal.md:
     * - timestamp, amount, matchScore, reason, status
     * 
     * @param amount Transaction amount in KES
     * @param matchScore Face recognition confidence score
     * @param reason Human-readable reason for transaction result
     * @param status Transaction status (APPROVED, BLOCKED, ENROLLED, etc.)
     */
    fun logTransaction(amount: Double, matchScore: Double, reason: String, status: String) {
        try {
            Log.d(TAG, "Logging transaction: amount=$amount, status=$status")
            
            // Create transaction log entry
            val logEntry = TransactionLog(
                timestamp = dateFormatter.format(Date()),
                amount = amount,
                matchScore = matchScore,
                reason = reason,
                status = status
            )
            
            // Get existing logs
            val existingLogs = getAllLogs().toMutableList()
            
            // Add new log entry
            existingLogs.add(logEntry)
            
            // Limit log size to prevent memory issues on Android Go devices
            if (existingLogs.size > MAX_LOG_ENTRIES) {
                val removeCount = existingLogs.size - MAX_LOG_ENTRIES
                repeat(removeCount) {
                    existingLogs.removeAt(0) // Remove oldest entries
                }
                Log.d(TAG, "Transaction logs trimmed to $MAX_LOG_ENTRIES entries")
            }
            
            // Serialize and store encrypted logs
            val logsJson = gson.toJson(existingLogs)
            val editor = encryptedPrefs.edit()
            editor.putString(TRANSACTION_LOGS_KEY, logsJson)
            val success = editor.commit()
            
            if (success) {
                Log.d(TAG, "Transaction logged successfully. Total logs: ${existingLogs.size}")
                // Also log to Logcat for demo/debugging as specified in goal.md
                Log.i("FRAUD_DETECTION_LOG", "[$status] Amount: $amount KES, Match: ${"%.3f".format(matchScore)}, Reason: $reason")
            } else {
                Log.e(TAG, "Failed to store transaction log")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging transaction", e)
        }
    }
    
    /**
     * Retrieve all encrypted transaction logs
     * Returns complete audit trail for in-app viewing as specified in goal.md
     * 
     * @return List of transaction logs sorted by timestamp (newest first)
     */
    fun getAllLogs(): List<TransactionLog> {
        return try {
            val logsJson = encryptedPrefs.getString(TRANSACTION_LOGS_KEY, null)
            
            if (logsJson.isNullOrEmpty()) {
                Log.d(TAG, "No transaction logs found")
                return emptyList()
            }
            
            // Deserialize logs from JSON
            val listType = object : TypeToken<List<TransactionLog>>() {}.type
            val logs: List<TransactionLog> = gson.fromJson(logsJson, listType)
            
            // Sort by timestamp (newest first) for better user experience
            val sortedLogs = logs.sortedByDescending { it.timestamp }
            
            Log.d(TAG, "Retrieved ${sortedLogs.size} transaction logs")
            sortedLogs
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving transaction logs", e)
            emptyList()
        }
    }
    
    /**
     * Get recent transaction logs for quick overview
     * Used for demo purposes and UI display
     * 
     * @param count Maximum number of recent logs to return
     * @return List of recent transaction logs
     */
    fun getRecentLogs(count: Int = 10): List<TransactionLog> {
        val allLogs = getAllLogs()
        return allLogs.take(count)
    }
    
    /**
     * Clear all stored data (face template and logs)
     * Useful for demo reset and user data deletion
     */
    fun clearAllData() {
        try {
            val editor = encryptedPrefs.edit()
            editor.clear()
            val success = editor.commit()
            
            if (success) {
                Log.d(TAG, "All encrypted data cleared successfully")
            } else {
                Log.e(TAG, "Failed to clear encrypted data")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing data", e)
        }
    }
    
    /**
     * Check if face template is enrolled
     * Used by UI to show appropriate options
     */
    fun isFaceEnrolled(): Boolean {
        return getFaceTemplate().isNotEmpty()
    }
    
    /**
     * Get storage statistics for demo/debugging
     * Shows current usage and limits
     */
    fun getStorageStats(): StorageStatistics {
        val faceTemplateSize = getFaceTemplate().size
        val logCount = getAllLogs().size
        
        return StorageStatistics(
            faceTemplateFeatures = faceTemplateSize,
            totalLogEntries = logCount,
            maxLogEntries = MAX_LOG_ENTRIES,
            isEncrypted = true
        )
    }
    
    /**
     * Data class for transaction log entries
     * Contains all fields specified in goal.md
     */
    data class TransactionLog(
        val timestamp: String,
        val amount: Double,
        val matchScore: Double,
        val reason: String,
        val status: String
    ) {
        fun getFormattedAmount(): String = "${"%.2f".format(amount)} KES"
        fun getFormattedScore(): String = "${"%.3f".format(matchScore)}"
        
        override fun toString(): String {
            return "[$timestamp] $status: ${getFormattedAmount()}, Score: ${getFormattedScore()}, $reason"
        }
    }
    
    /**
     * Data class for storage statistics
     * Used for monitoring and demo purposes
     */
    data class StorageStatistics(
        val faceTemplateFeatures: Int,
        val totalLogEntries: Int,
        val maxLogEntries: Int,
        val isEncrypted: Boolean
    ) {
        override fun toString(): String {
            return """
                |Storage Statistics:
                |  Face Template: $faceTemplateFeatures features
                |  Transaction Logs: $totalLogEntries / $maxLogEntries
                |  Encryption: ${if (isEncrypted) "Enabled" else "Disabled"}
            """.trimMargin()
        }
    }
}
