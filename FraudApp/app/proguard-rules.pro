# ProGuard rules for fraud detection app

# Keep all classes and methods needed for fraud detection functionality
-keep class com.frauddetection.** { *; }

# OpenCV native library protection
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }

# EncryptedSharedPreferences protection
-keep class androidx.security.crypto.** { *; }

# CameraX protection
-keep class androidx.camera.** { *; }
-keepclassmembers class androidx.camera.** { *; }

# Gson protection for JSON serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes for secure storage
-keep class com.frauddetection.SecureStore$TransactionLog { *; }
-keep class com.frauddetection.FaceRecognizer$FeaturePoint { *; }
-keep class com.frauddetection.FraudEngine$FraudStatistics { *; }

# General Android optimizations for Go devices
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
