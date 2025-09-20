package com.frauddetection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * FaceRecognizer - Handles offline face enrollment and verification
 * 
 * Uses OpenCV ORB features for lightweight face recognition suitable for Android Go devices.
 * All processing happens on-device with no network calls required.
 * 
 * Key methods as specified in goal.md:
 * - enroll(bitmap: Bitmap): Boolean → store user's face template
 * - verify(bitmap: Bitmap): Pair<Double, Boolean> → compare live face with template
 */
class FaceRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceRecognizer"
        private const val MATCH_THRESHOLD = 0.7 // Minimum similarity score for face match
        private const val MIN_FEATURES = 50 // Minimum ORB features required for reliable matching
        private const val MAX_FEATURES = 500 // Maximum features to keep processing lightweight
    }
    
    private val secureStore = SecureStore(context)
    private var orb: ORB? = null
    
    init {
        try {
            // Initialize OpenCV ORB detector for feature extraction
            // ORB is lightweight and suitable for mobile devices
            orb = ORB.create(MAX_FEATURES, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20)
            Log.d(TAG, "FaceRecognizer initialized with OpenCV ORB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenCV ORB", e)
        }
    }
    
    /**
     * Enroll user's face for biometric authentication
     * Extracts ORB features and stores encrypted template on-device
     * 
     * @param bitmap Face image captured from front camera
     * @return Boolean indicating successful enrollment
     */
    fun enroll(bitmap: Bitmap): Boolean {
        return try {
            Log.d(TAG, "Starting face enrollment process")
            
            // Convert bitmap to OpenCV Mat for processing
            val faceMat = bitmapToMat(bitmap)
            
            // Preprocess image for better feature extraction
            val processedMat = preprocessImage(faceMat)
            
            // Extract ORB features from face
            val features = extractFeatures(processedMat)
            
            if (features.isEmpty()) {
                Log.w(TAG, "No facial features detected during enrollment")
                return false
            }
            
            if (features.size < MIN_FEATURES) {
                Log.w(TAG, "Insufficient facial features detected: ${features.size} < $MIN_FEATURES")
                return false
            }
            
            // Store encrypted face template using SecureStore
            val enrollmentSuccess = secureStore.storeFaceTemplate(features)
            
            if (enrollmentSuccess) {
                Log.d(TAG, "Face enrolled successfully with ${features.size} features")
            } else {
                Log.e(TAG, "Failed to store face template securely")
            }
            
            enrollmentSuccess
            
        } catch (e: Exception) {
            Log.e(TAG, "Face enrollment failed", e)
            false
        }
    }
    
    /**
     * Verify live face against enrolled template
     * Performs offline biometric matching using ORB feature comparison
     * 
     * @param bitmap Live face image from camera
     * @return Pair<Double, Boolean> - (match score, verification result)
     */
    fun verify(bitmap: Bitmap): Pair<Double, Boolean> {
        return try {
            Log.d(TAG, "Starting face verification process")
            
            // Retrieve stored face template
            val storedFeatures = secureStore.getFaceTemplate()
            if (storedFeatures.isEmpty()) {
                Log.w(TAG, "No enrolled face template found")
                return Pair(0.0, false)
            }
            
            // Convert live image to OpenCV Mat
            val liveMat = bitmapToMat(bitmap)
            
            // Preprocess for consistent comparison
            val processedMat = preprocessImage(liveMat)
            
            // Extract features from live face
            val liveFeatures = extractFeatures(processedMat)
            
            if (liveFeatures.isEmpty() || liveFeatures.size < MIN_FEATURES) {
                Log.w(TAG, "Insufficient features in live image: ${liveFeatures.size}")
                return Pair(0.0, false)
            }
            
            // Calculate similarity between stored and live features
            val matchScore = calculateSimilarity(storedFeatures, liveFeatures)
            val isMatch = matchScore >= MATCH_THRESHOLD
            
            Log.d(TAG, "Face verification complete: score=${"%.3f".format(matchScore)}, match=$isMatch")
            
            Pair(matchScore, isMatch)
            
        } catch (e: Exception) {
            Log.e(TAG, "Face verification failed", e)
            Pair(0.0, false)
        }
    }
    
    /**
     * Convert Android Bitmap to OpenCV Mat for processing
     * Optimized for memory efficiency on Android Go devices
     */
    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }
    
    /**
     * Preprocess face image for consistent feature extraction
     * - Convert to grayscale for faster processing
     * - Apply histogram equalization for better feature detection
     * - Resize to fixed dimensions for consistency
     */
    private fun preprocessImage(inputMat: Mat): Mat {
        val grayMat = Mat()
        val processedMat = Mat()
        
        try {
            // Convert to grayscale (reduces processing by ~3x)
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                grayMat = inputMat.clone()
            }
            
            // Apply histogram equalization for consistent lighting
            Imgproc.equalizeHist(grayMat, processedMat)
            
            // Resize to standard dimensions (200x200) for consistency and speed
            val resizedMat = Mat()
            Imgproc.resize(processedMat, resizedMat, Size(200.0, 200.0))
            
            return resizedMat
            
        } catch (e: Exception) {
            Log.e(TAG, "Image preprocessing failed", e)
            return inputMat
        }
    }
    
    /**
     * Extract ORB features from preprocessed face image
     * ORB features are rotation and scale invariant, suitable for mobile face recognition
     */
    private fun extractFeatures(mat: Mat): List<FeaturePoint> {
        return try {
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            
            // Detect and compute ORB features
            orb?.detectAndCompute(mat, Mat(), keypoints, descriptors)
            
            val features = mutableListOf<FeaturePoint>()
            val keypointArray = keypoints.toArray()
            
            // Convert OpenCV descriptors to our FeaturePoint format
            if (descriptors.rows() > 0 && keypointArray.isNotEmpty()) {
                for (i in 0 until minOf(descriptors.rows(), keypointArray.size)) {
                    val keypoint = keypointArray[i]
                    val descriptor = descriptors.row(i)
                    
                    // Convert descriptor to byte array for storage
                    val descriptorBytes = ByteArray(descriptor.cols())
                    descriptor.get(0, 0, descriptorBytes)
                    
                    features.add(
                        FeaturePoint(
                            x = keypoint.pt.x.toFloat(),
                            y = keypoint.pt.y.toFloat(),
                            descriptor = descriptorBytes
                        )
                    )
                }
            }
            
            Log.d(TAG, "Extracted ${features.size} ORB features")
            features
            
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction failed", e)
            emptyList()
        }
    }
    
    /**
     * Calculate similarity between two sets of facial features
     * Uses Hamming distance for ORB descriptor matching
     * Returns similarity score between 0.0 (no match) and 1.0 (perfect match)
     */
    private fun calculateSimilarity(template: List<FeaturePoint>, live: List<FeaturePoint>): Double {
        if (template.isEmpty() || live.isEmpty()) return 0.0
        
        try {
            var totalMatches = 0
            var goodMatches = 0
            val maxDistance = 50 // Maximum Hamming distance for a good match
            
            // Compare each template feature with all live features
            for (templateFeature in template) {
                var bestDistance = Int.MAX_VALUE
                
                for (liveFeature in live) {
                    val distance = hammingDistance(templateFeature.descriptor, liveFeature.descriptor)
                    if (distance < bestDistance) {
                        bestDistance = distance
                    }
                }
                
                totalMatches++
                if (bestDistance < maxDistance) {
                    goodMatches++
                }
            }
            
            // Calculate similarity ratio
            val similarity = if (totalMatches > 0) {
                goodMatches.toDouble() / totalMatches.toDouble()
            } else {
                0.0
            }
            
            Log.d(TAG, "Feature matching: $goodMatches/$totalMatches good matches, similarity=${"%.3f".format(similarity)}")
            
            return similarity
            
        } catch (e: Exception) {
            Log.e(TAG, "Similarity calculation failed", e)
            return 0.0
        }
    }
    
    /**
     * Calculate Hamming distance between two ORB descriptors
     * Hamming distance counts differing bits between binary descriptors
     */
    private fun hammingDistance(desc1: ByteArray, desc2: ByteArray): Int {
        if (desc1.size != desc2.size) return Int.MAX_VALUE
        
        var distance = 0
        for (i in desc1.indices) {
            // XOR bytes and count set bits
            var xor = (desc1[i].toInt() xor desc2[i].toInt()) and 0xFF
            while (xor != 0) {
                distance++
                xor = xor and (xor - 1) // Clear lowest set bit
            }
        }
        return distance
    }
    
    /**
     * Data class to represent facial feature points
     * Stores ORB keypoint location and descriptor for matching
     */
    data class FeaturePoint(
        val x: Float,
        val y: Float,
        val descriptor: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as FeaturePoint
            
            if (x != other.x) return false
            if (y != other.y) return false
            if (!descriptor.contentEquals(other.descriptor)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = x.hashCode()
            result = 31 * result + y.hashCode()
            result = 31 * result + descriptor.contentHashCode()
            return result
        }
    }
}
