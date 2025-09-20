# Face-to-Phone Android Prototype

## Goal

Build an **offline-first Android app in Kotlin** that demonstrates **real-time biometric fraud detection** for financial transactions.

The app should:

* Use **on-device face recognition** to verify users before transactions.
* Run **fraud detection rules locally** (no internet).
* Log all events securely on-device.
* Support a **demo flow** for hackathon judging.

---

## Features

### 1. Biometric Authentication (Face)

* Use **CameraX** with the front camera to capture face images.
* `FaceRecognizer` class (using **OpenCV** ORB/LBPH or lightweight TFLite):

  * `enroll(bitmap: Bitmap): Boolean` → store user’s face template.
  * `verify(bitmap: Bitmap): Pair<Double, Boolean>` → compare live face with template.
* Store biometric data securely using **EncryptedSharedPreferences**.
* No network calls — all face matching runs offline.

### 2. Fraud Detection Engine

* `FraudEngine` class checks for anomalies:

  * Transaction `amount > dailyLimit` (e.g. 10,000 KES).
  * Transaction deviates >3σ from historical mean.
  * `simChanged == true` (simulated via UI toggle).
* Return reason string for blocked actions (e.g. “Blocked: Face mismatch”).

### 3. Secure Logging

* `SecureStore` class using **EncryptedSharedPreferences**:

  * Save enrolled face template.
  * Save encrypted logs with:

    * timestamp
    * amount
    * matchScore
    * reason
    * status
* Logs can be viewed in-app (text list or dialog) and in Logcat.

### 4. UI / Demo Flow

* `MainActivity` + `activity_main.xml`:

  * Camera preview at top.
  * Input field: transaction amount.
  * Buttons: **Enroll**, **Verify Transaction**, **Toggle SIM Change**, **View Logs**.
  * Status text for results (Approved / Blocked + reason).
* Demo should work offline in airplane mode.

---

## File Structure

* `MainActivity.kt` → UI logic, flow orchestration.
* `FaceRecognizer.kt` → Face enrollment & verification.
* `FraudEngine.kt` → Offline fraud rules.
* `SecureStore.kt` → Secure storage & logging.
* `activity_main.xml` → Layout with camera preview, input, buttons, status text.

---

## Constraints

* Must run **offline**, no internet required.
* Use lightweight ML / OpenCV for face recognition.
* Keep all biometric + log data **on-device and encrypted**.
* Aim for **sub-second latency** on verification + fraud detection.
* Target low-end Android Go devices.

---

## Demo Scenario

1. Enroll user face.
2. Legit transaction → approved.
3. Impostor tries → blocked (face mismatch).
4. Real user attempts large transfer → blocked (fraud rule).
5. SIM change simulated → blocked.
6. Logs displayed showing reasons.

---

👉 Save this as `COPILOT_CONTEXT.md` at the root of your project. Then, when you prompt Copilot (e.g. *“Generate `FaceRecognizer.kt` based on COPILOT\_CONTEXT.md”*), it will follow these requirements.
