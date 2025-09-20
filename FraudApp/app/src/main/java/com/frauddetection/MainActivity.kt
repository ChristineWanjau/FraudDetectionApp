package com.frauddetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MainActivity - UI logic and flow orchestration for the fraud detection app
 * 
 * Features as specified in goal.md:
 * - Camera preview for face capture using CameraX
 * - Face enrollment and verification using OpenCV
 * - Transaction processing with offline fraud detection
 * - Secure logging with EncryptedSharedPreferences
 * - Demo flow that works offline (airplane mode)
 * 
 * Optimized for Android Go devices with sub-second latency.
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_CODE = 100
    }
    
    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var amountEditText: EditText
    private lateinit var enrollButton: Button
    private lateinit var verifyButton: Button
    private lateinit var simChangeSwitch: Switch
    private lateinit var viewLogsButton: Button
    private lateinit var statusText: TextView
    
    // Core Components - all work offline as specified in goal.md
    private lateinit var faceRecognizer: FaceRecognizer
    private lateinit var fraudEngine: FraudEngine
    private lateinit var secureStore: SecureStore
    
    // Camera Components
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentBitmap: Bitmap? = null
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            showError("Camera permission is required for face recognition")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeComponents()
        setupUI()
        requestCameraPermission()
    }
    
    /**
     * Initialize core components for offline fraud detection
     * All components work without internet as specified in goal.md
     */
    private fun initializeComponents() {
        faceRecognizer = FaceRecognizer(this)
        fraudEngine = FraudEngine()
        secureStore = SecureStore(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        Log.d(TAG, "Core components initialized for offline operation")
    }
    
    /**
     * Setup UI components and click listeners
     * Implements exact button layout from goal.md requirements
     */
    private fun setupUI() {
        previewView = findViewById(R.id.previewView)
        amountEditText = findViewById(R.id.amountEditText)
        enrollButton = findViewById(R.id.enrollButton)
        verifyButton = findViewById(R.id.verifyButton)
        simChangeSwitch = findViewById(R.id.simChangeSwitch)
        viewLogsButton = findViewById(R.id.viewLogsButton)
        statusText = findViewById(R.id.statusText)
        
        // Enroll user face - stores biometric template securely using EncryptedSharedPreferences
        enrollButton.setOnClickListener {
            currentBitmap?.let { bitmap ->
                enrollFace(bitmap)
            } ?: showError("No face detected. Please position your face in the camera.")
        }
        
        // Verify transaction with face + fraud detection (sub-second latency target)
        verifyButton.setOnClickListener {
            val amount = amountEditText.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                showError("Please enter a valid transaction amount")
                return@setOnClickListener
            }
            
            currentBitmap?.let { bitmap ->
                processTransaction(bitmap, amount)
            } ?: showError("No face detected. Please position your face in the camera.")
        }
        
        // View encrypted logs stored on-device (can be viewed in-app and Logcat)
        viewLogsButton.setOnClickListener {
            showLogs()
        }
        
        updateStatus("Ready - Position your face in the camera")
    }
    
    /**
     * Request camera permission required for face recognition
     */
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * Start camera with CameraX for real-time face capture
     * Uses front camera as specified in goal.md for user-facing biometric authentication
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Preview use case - shows camera feed to user
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                // Image analysis for face detection and capture
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(cameraExecutor, FaceImageAnalyzer { bitmap ->
                            // Store latest face bitmap for enrollment/verification
                            currentBitmap = bitmap
                        })
                    }
                
                // Use front camera for user-facing authentication
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                // Bind use cases to lifecycle
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                
                Log.d(TAG, "Camera started successfully with front camera")
                
            } catch (exc: Exception) {
                Log.e(TAG, "Camera startup failed", exc)
                showError("Failed to start camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    /**
     * Enroll user's face for biometric authentication
     * Stores encrypted face template using EncryptedSharedPreferences as specified in goal.md
     */
    private fun enrollFace(bitmap: Bitmap) {
        updateStatus("Enrolling face...")
        
        Thread {
            val startTime = System.currentTimeMillis()
            val enrollmentResult = faceRecognizer.enroll(bitmap)
            val processingTime = System.currentTimeMillis() - startTime
            
            runOnUiThread {
                if (enrollmentResult) {
                    updateStatus("Face enrolled successfully ✓ (${processingTime}ms)")
                    secureStore.logTransaction(
                        amount = 0.0,
                        matchScore = 1.0,
                        reason = "Face enrollment completed in ${processingTime}ms",
                        status = "ENROLLED"
                    )
                    Toast.makeText(this, "Face enrolled successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    updateStatus("Face enrollment failed ✗")
                    showError("Failed to enroll face. Please try again with better lighting.")
                }
            }
        }.start()
    }
    
    /**
     * Process transaction with biometric verification and fraud detection
     * Implements complete flow as specified in goal.md:
     * 1. Face verification using OpenCV ORB/LBPH
     * 2. Fraud detection rules (amount > dailyLimit, 3σ deviation, SIM change)
     * 3. Secure logging with all details
     * 
     * Target: sub-second latency for Android Go devices
     */
    private fun processTransaction(bitmap: Bitmap, amount: Double) {
        updateStatus("Processing transaction...")
        
        Thread {
            val startTime = System.currentTimeMillis()
            
            // Step 1: Face verification (offline biometric matching using OpenCV)
            val (matchScore, faceVerified) = faceRecognizer.verify(bitmap)
            val faceVerificationTime = System.currentTimeMillis() - startTime
            
            // Step 2: Fraud detection rules (offline analysis)
            val fraudStartTime = System.currentTimeMillis()
            val simChanged = simChangeSwitch.isChecked
            val fraudResult = fraudEngine.checkTransaction(amount, simChanged)
            val fraudDetectionTime = System.currentTimeMillis() - fraudStartTime
            
            // Step 3: Final decision logic
            val approved = faceVerified && fraudResult.first
            val reason = when {
                !faceVerified -> "Blocked: Face mismatch (score: ${"%.3f".format(matchScore)})"
                !fraudResult.first -> "Blocked: ${fraudResult.second}"
                else -> "Approved: Transaction verified"
            }
            
            val totalProcessingTime = System.currentTimeMillis() - startTime
            
            // Step 4: Secure logging with EncryptedSharedPreferences
            val status = if (approved) "APPROVED" else "BLOCKED"
            secureStore.logTransaction(amount, matchScore, reason, status)
            
            runOnUiThread {
                updateStatus("$reason\n(Face: ${faceVerificationTime}ms, Fraud: ${fraudDetectionTime}ms, Total: ${totalProcessingTime}ms)")
                
                // Visual feedback with color coding
                val color = if (approved) {
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                }
                statusText.setTextColor(color)
                
                // Log for debugging/demo purposes (visible in Logcat as specified in goal.md)
                Log.i(TAG, "Transaction processed: Amount=$amount KES, Face=$faceVerified, Fraud=${fraudResult.first}, Final=$approved, Time=${totalProcessingTime}ms")
            }
        }.start()
    }
    
    /**
     * Display encrypted transaction logs stored on-device
     * Shows audit trail as specified in goal.md with timestamp, amount, matchScore, reason, status
     */
    private fun showLogs() {
        val logs = secureStore.getAllLogs()
        
        val logText = if (logs.isEmpty()) {
            "No transaction logs found"
        } else {
            logs.take(20).joinToString("\n\n") { log ->
                """
                🕒 ${log.timestamp}
                💰 Amount: ${log.getFormattedAmount()}
                📊 Match Score: ${log.getFormattedScore()}
                ⚡ Status: ${log.status}
                📝 Reason: ${log.reason}
                """.trimIndent()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Transaction Logs (${logs.size} entries)")
            .setMessage(logText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear All") { _, _ ->
                clearAllData()
            }
            .show()
    }
    
    /**
     * Clear all data for demo reset
     */
    private fun clearAllData() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will remove all enrolled faces and transaction logs. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                secureStore.clearAllData()
                fraudEngine.reset()
                updateStatus("All data cleared - Ready for new demo")
                Toast.makeText(this, "Data cleared successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    /**
     * Update status text with current operation feedback
     */
    private fun updateStatus(message: String) {
        statusText.text = message
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        Log.d(TAG, "Status: $message")
    }
    
    /**
     * Show error message to user
     */
    private fun showError(message: String) {
        updateStatus("Error: $message")
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Error: $message")
    }
    
    /**
     * Image analyzer for real-time face detection and capture
     * Converts CameraX ImageProxy to Bitmap for OpenCV processing
     * Optimized for Android Go devices with memory efficiency
     */
    private inner class FaceImageAnalyzer(
        private val onFaceDetected: (Bitmap) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        override fun analyze(image: ImageProxy) {
            try {
                // Convert ImageProxy to Bitmap for OpenCV face recognition
                val bitmap = imageProxyToBitmap(image)
                bitmap?.let { onFaceDetected(it) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed", e)
            } finally {
                image.close()
            }
        }
        
        /**
         * Convert CameraX ImageProxy to Bitmap
         * Handles YUV_420_888 format from camera
         * Optimized for memory efficiency on Android Go devices
         */
        private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
            return try {
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer
                
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()
                
                val nv21 = ByteArray(ySize + uSize + vSize)
                
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
                
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 50, out)
                val imageBytes = out.toByteArray()
                
                // Scale down for processing efficiency on Android Go
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2 // Reduce image size by 2x for faster processing
                }
                
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
                null
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        Log.d(TAG, "MainActivity destroyed, resources cleaned up")
    }
}
