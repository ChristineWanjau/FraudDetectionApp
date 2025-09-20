package com.frauddetection

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * FraudEngine - Offline fraud detection for financial transactions
 * 
 * Implements fraud detection rules as specified in goal.md:
 * - Transaction amount > dailyLimit (e.g. 10,000 KES)
 * - Transaction deviates >3σ from historical mean
 * - simChanged == true (simulated via UI toggle)
 * 
 * All processing happens offline with no network calls required.
 * Optimized for sub-second latency on Android Go devices.
 */
class FraudEngine {
    
    companion object {
        private const val TAG = "FraudEngine"
        private const val DAILY_LIMIT_KES = 10000.0 // Daily transaction limit in KES
        private const val SIGMA_THRESHOLD = 3.0 // Standard deviation threshold for anomaly detection
        private const val MIN_HISTORICAL_TRANSACTIONS = 5 // Minimum transactions needed for statistical analysis
    }
    
    // Store transaction history for statistical fraud detection
    private val transactionHistory = mutableListOf<Double>()
    
    /**
     * Check transaction for fraud indicators
     * Combines multiple fraud detection rules for comprehensive security
     * 
     * @param amount Transaction amount in KES
     * @param simChanged Whether SIM card has been changed (security risk)
     * @return Pair<Boolean, String> - (approved, reason)
     */
    fun checkTransaction(amount: Double, simChanged: Boolean): Pair<Boolean, String> {
        Log.d(TAG, "Analyzing transaction: amount=$amount KES, simChanged=$simChanged")
        
        try {
            // Rule 1: Check daily limit (as specified in goal.md)
            val dailyLimitCheck = checkDailyLimit(amount)
            if (!dailyLimitCheck.first) {
                Log.w(TAG, "Transaction blocked by daily limit rule")
                return dailyLimitCheck
            }
            
            // Rule 2: Check for SIM change (security indicator)
            val simChangeCheck = checkSimChange(simChanged)
            if (!simChangeCheck.first) {
                Log.w(TAG, "Transaction blocked by SIM change rule")
                return simChangeCheck
            }
            
            // Rule 3: Statistical anomaly detection (3σ rule)
            val anomalyCheck = checkStatisticalAnomaly(amount)
            if (!anomalyCheck.first) {
                Log.w(TAG, "Transaction blocked by statistical anomaly rule")
                return anomalyCheck
            }
            
            // All fraud checks passed - approve transaction
            addToHistory(amount)
            Log.d(TAG, "Transaction approved - all fraud checks passed")
            return Pair(true, "Transaction approved")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fraud detection error", e)
            // Fail secure - block transaction on error
            return Pair(false, "System error - transaction blocked for security")
        }
    }
    
    /**
     * Rule 1: Daily limit check
     * Block transactions exceeding 10,000 KES as specified in goal.md
     */
    private fun checkDailyLimit(amount: Double): Pair<Boolean, String> {
        return if (amount > DAILY_LIMIT_KES) {
            Pair(false, "Amount exceeds daily limit of ${DAILY_LIMIT_KES.toInt()} KES")
        } else {
            Pair(true, "Daily limit check passed")
        }
    }
    
    /**
     * Rule 2: SIM change detection
     * Block transactions when SIM card has been changed (security risk)
     * This simulates detection of SIM swap attacks
     */
    private fun checkSimChange(simChanged: Boolean): Pair<Boolean, String> {
        return if (simChanged) {
            Pair(false, "SIM card change detected - transaction blocked for security")
        } else {
            Pair(true, "SIM change check passed")
        }
    }
    
    /**
     * Rule 3: Statistical anomaly detection
     * Block transactions that deviate >3σ from historical mean
     * Implements the 3-sigma rule for outlier detection as specified in goal.md
     */
    private fun checkStatisticalAnomaly(amount: Double): Pair<Boolean, String> {
        // Need sufficient historical data for statistical analysis
        if (transactionHistory.size < MIN_HISTORICAL_TRANSACTIONS) {
            Log.d(TAG, "Insufficient transaction history for anomaly detection (${transactionHistory.size} < $MIN_HISTORICAL_TRANSACTIONS)")
            return Pair(true, "Statistical check skipped - building history")
        }
        
        try {
            // Calculate historical mean and standard deviation
            val mean = calculateMean(transactionHistory)
            val stdDev = calculateStandardDeviation(transactionHistory, mean)
            
            // Check if current transaction deviates >3σ from mean
            val deviation = abs(amount - mean)
            val sigmaDeviation = if (stdDev > 0) deviation / stdDev else 0.0
            
            Log.d(TAG, "Statistical analysis: mean=${"%.2f".format(mean)}, stdDev=${"%.2f".format(stdDev)}, deviation=${"%.2f".format(sigmaDeviation)}σ")
            
            return if (sigmaDeviation > SIGMA_THRESHOLD) {
                Pair(false, "Transaction anomaly detected (${String.format("%.1f", sigmaDeviation)}σ deviation)")
            } else {
                Pair(true, "Statistical anomaly check passed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Statistical analysis failed", e)
            // On error, allow transaction to avoid false positives
            return Pair(true, "Statistical check error - transaction allowed")
        }
    }
    
    /**
     * Add approved transaction to history for future statistical analysis
     * Maintains sliding window to prevent memory issues on Android Go devices
     */
    private fun addToHistory(amount: Double) {
        transactionHistory.add(amount)
        
        // Limit history size to prevent memory issues on low-end devices
        val maxHistorySize = 100
        if (transactionHistory.size > maxHistorySize) {
            // Remove oldest transactions to maintain sliding window
            val removeCount = transactionHistory.size - maxHistorySize
            repeat(removeCount) {
                transactionHistory.removeAt(0)
            }
            Log.d(TAG, "Transaction history trimmed to $maxHistorySize entries")
        }
        
        Log.d(TAG, "Transaction added to history. Total history: ${transactionHistory.size}")
    }
    
    /**
     * Calculate arithmetic mean of transaction amounts
     * Used for statistical fraud detection baseline
     */
    private fun calculateMean(values: List<Double>): Double {
        return if (values.isEmpty()) 0.0 else values.sum() / values.size
    }
    
    /**
     * Calculate standard deviation of transaction amounts
     * Used for 3-sigma outlier detection as specified in goal.md
     */
    private fun calculateStandardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size <= 1) return 0.0
        
        val sumSquaredDifferences = values.sumOf { (it - mean) * (it - mean) }
        val variance = sumSquaredDifferences / (values.size - 1) // Sample standard deviation
        
        return sqrt(variance)
    }
    
    /**
     * Get current fraud detection statistics for debugging/demo
     * Useful for hackathon demonstration and troubleshooting
     */
    fun getStatistics(): FraudStatistics {
        val mean = if (transactionHistory.isNotEmpty()) calculateMean(transactionHistory) else 0.0
        val stdDev = if (transactionHistory.isNotEmpty()) calculateStandardDeviation(transactionHistory, mean) else 0.0
        
        return FraudStatistics(
            transactionCount = transactionHistory.size,
            dailyLimit = DAILY_LIMIT_KES,
            historicalMean = mean,
            standardDeviation = stdDev,
            sigmaThreshold = SIGMA_THRESHOLD
        )
    }
    
    /**
     * Reset fraud detection state
     * Useful for demo scenarios and testing
     */
    fun reset() {
        transactionHistory.clear()
        Log.d(TAG, "Fraud detection state reset")
    }
    
    /**
     * Data class containing fraud detection statistics
     * Used for demo purposes and system monitoring
     */
    data class FraudStatistics(
        val transactionCount: Int,
        val dailyLimit: Double,
        val historicalMean: Double,
        val standardDeviation: Double,
        val sigmaThreshold: Double
    ) {
        override fun toString(): String {
            return """
                |Fraud Detection Statistics:
                |  Transactions: $transactionCount
                |  Daily Limit: ${dailyLimit.toInt()} KES
                |  Historical Mean: ${"%.2f".format(historicalMean)} KES
                |  Std Deviation: ${"%.2f".format(standardDeviation)} KES
                |  Sigma Threshold: ${sigmaThreshold}σ
            """.trimMargin()
        }
    }
}
