package com.yatri

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.yatri.patrol.PatrolApi
import com.yatri.patrol.PatrolStatusResponse
import com.yatri.patrol.EndPatrolBody
import com.yatri.patrol.ScanCheckpointPayload
import com.yatri.net.Network
import com.yatri.AppConfig
import kotlinx.coroutines.CoroutineScope
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import retrofit2.create
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ActivePatrolActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sessionId: String? = null
    private var roleId: String? = null
    private val TAG = "ActivePatrol"
    private var remainingCheckpointsCount: Int = -1
    private var lastScanErrorAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_patrol)
        
        Log.d(TAG, "========== ActivePatrolActivity STARTED ==========")
        Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d(TAG, "Android Version: ${android.os.Build.VERSION.RELEASE}")
        Log.d(TAG, "App Package: $packageName")
        
        previewView = findViewById(R.id.previewView)
        sessionId = intent.getStringExtra("sessionId")
        roleId = intent.getStringExtra("roleId")
        Log.d(TAG, "onCreate - sessionId=$sessionId roleId=$roleId routeName=${intent.getStringExtra("routeName")}")
        
        // Bind route name and tentative status from intent (for fast UI)
        intent.getStringExtra("routeName")?.let { findViewById<TextView>(R.id.tvRouteName).text = it }
        findViewById<TextView>(R.id.tvStatus).text = "Status: IN_PROGRESS"
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Wire up End Patrol button
        findViewById<Button>(R.id.btnEndPatrol).setOnClickListener {
            confirmAndEndPatrol()
        }
        
        Log.d(TAG, "Calling requestPermissions()")
        requestPermissions()
        
        // Initial status fetch to update UI
        Log.d(TAG, "Calling fetchPatrolStatus()")
        fetchPatrolStatus()
    }

    override fun onResume() {
        super.onResume()
        // Extra guard: ensure status is fetched when screen becomes visible
        if (!sessionId.isNullOrEmpty() && !roleId.isNullOrEmpty()) {
            Log.d(TAG, "onResume - refreshing patrol status")
            fetchPatrolStatus()
        }
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        Log.d(TAG, "Checking permissions...")
        Log.d(TAG, "CAMERA: ${if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        Log.d(TAG, "LOCATION: ${if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        
        if (missing.isEmpty()) {
            Log.d(TAG, "All permissions granted, starting camera")
            startCamera()
        } else {
            Log.d(TAG, "Missing permissions: ${missing.joinToString()}, requesting...")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun fetchPatrolStatus() {
        val sid = sessionId ?: return
        val rid = roleId ?: return
        Log.d(TAG, "Fetching patrol status for sessionId=$sid roleId=$rid")
        val statusUrl = "${AppConfig.API_BASE_URL}employee/patrol/sessions/$sid/status?roleId=$rid"
        Log.d(TAG, "STATUS API URL: $statusUrl")
        Log.d(TAG, "Method: GET")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = Network.retrofit.create(PatrolApi::class.java)
                val resp = api.getPatrolStatus(sid, rid)
                Log.d(TAG, "=== STATUS API RESPONSE ===")
                Log.d(TAG, "URL: $statusUrl")
                Log.d(TAG, "Status Code: ${resp.code()}")
                Log.d(TAG, "Body: ${resp.body()}")
                Log.d(TAG, "Error: ${try { resp.errorBody()?.string() } catch (_: Exception) { null }}")
                try {
                    val b = android.os.Bundle().apply {
                        putString("url", statusUrl)
                        putString("method", "GET")
                        putInt("status_code", resp.code())
                    }
                    Firebase.analytics.logEvent("patrol_status", b)
                } catch (_: Exception) { }
                if (resp.isSuccessful) {
                    val data: PatrolStatusResponse? = resp.body()?.data
                    runOnUiThread {
                        data?.let {
                            remainingCheckpointsCount = it.remainingCheckpointsCount
                            findViewById<TextView>(R.id.tvStatus).text = "Status: ${it.status}"
                            // Enable End Patrol only when no checkpoints remain
                            val btn = findViewById<Button>(R.id.btnEndPatrol)
                            btn.isEnabled = it.remainingCheckpointsCount == 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch patrol status", e)
            }
        }
    }

    private fun confirmAndEndPatrol() {
        if (sessionId.isNullOrEmpty() || roleId.isNullOrEmpty()) {
            Toast.makeText(this, "Session or role missing.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("End Patrol")
            .setMessage("Are you sure you want to end this patrol?")
            .setPositiveButton("End") { _, _ -> endPatrol() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endPatrol() {
        val sid = sessionId ?: return
        val rid = roleId ?: return
        Log.d(TAG, "=== END PATROL API CALL ===")
        Log.d(TAG, "Session ID: $sid")
        Log.d(TAG, "Role ID: $rid")
        val fullUrl = "${AppConfig.API_BASE_URL}employee/patrol/sessions/$sid/end?roleId=$rid"
        Log.d(TAG, "URL: $fullUrl")
        Log.d(TAG, "Method: POST")
        val body = EndPatrolBody(notes = "Patrol completed successfully.")
        Log.d(TAG, "Request body: $body")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = Network.retrofit.create(PatrolApi::class.java)
                val resp = api.endSession(sid, rid, body)
                Log.d(TAG, "=== END PATROL API RESPONSE ===")
                Log.d(TAG, "Status Code: ${resp.code()}")
                Log.d(TAG, "Response Body: ${resp.body()}")
                Log.d(TAG, "Error Body: ${try { resp.errorBody()?.string() } catch (_: Exception) { null }}")
                try {
                    val b = android.os.Bundle().apply {
                        putString("url", fullUrl)
                        putString("method", "POST")
                        putInt("status_code", resp.code())
                    }
                    Firebase.analytics.logEvent("patrol_end", b)
                } catch (_: Exception) { }
                runOnUiThread {
                    if (resp.isSuccessful && resp.body()?.status == "success") {
                        Toast.makeText(this@ActivePatrolActivity, "Patrol ended successfully.", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        val error = resp.errorBody()?.string()
                        Log.e(TAG, "End patrol failed. Code=${resp.code()} body=$error")
                        AlertDialog.Builder(this@ActivePatrolActivity)
                            .setTitle("End Patrol Failed")
                            .setMessage(resp.body()?.message ?: error ?: "Unknown error")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception ending patrol", e)
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, e.message ?: "Error ending patrol", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera and Location permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        android.util.Log.d(TAG, "=== STARTING CAMERA ===")
        android.util.Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        android.util.Log.d(TAG, "Android Version: ${android.os.Build.VERSION.RELEASE}")
        
        runOnUiThread {
            Toast.makeText(this@ActivePatrolActivity, "Initializing camera...", Toast.LENGTH_SHORT).show()
        }
        
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                android.util.Log.d(TAG, "Camera provider obtained successfully")
                
                val preview = androidx.camera.core.Preview.Builder()
                    .setTargetResolution(android.util.Size(640, 480)) // Much lower resolution for stability
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                android.util.Log.d(TAG, "Creating ImageAnalysis with basic settings for maximum compatibility")
                val imageAnalyzerBuilder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(320, 240)) // Very low resolution for analysis
                
                // Use the most compatible format - don't try to be smart about device detection
                // Let the system choose the best format
                android.util.Log.d(TAG, "Using default image format for maximum compatibility")
                
                val imageAnalyzer = imageAnalyzerBuilder.build().also {
                    android.util.Log.d(TAG, "Setting QRCodeAnalyzer")
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrData ->
                        android.util.Log.d(TAG, "QR code detected: $qrData")
                        runOnUiThread { handleQrResult(qrData) }
                    })
                }
                
                android.util.Log.d(TAG, "Binding camera to lifecycle")
                cameraProvider.unbindAll()
                
                // Try different camera selectors for better device compatibility
                val cameraSelector = try {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Default back camera not available, trying Builder approach")
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                }
                
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                android.util.Log.d(TAG, "Camera bound successfully")
                
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, "Camera ready. Point at QR code", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Camera initialization error: ${e.javaClass.simpleName} - ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
                    // Try fallback approach
                    tryFallbackCamera()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun tryFallbackCamera() {
        android.util.Log.d(TAG, "Attempting fallback camera initialization")
        try {
            // Simplified camera setup for problematic devices
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(320, 240))
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                    
                    imageAnalyzer.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrData ->
                        runOnUiThread { handleQrResult(qrData) }
                    })
                    
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                    
                    runOnUiThread {
                        Toast.makeText(this@ActivePatrolActivity, "Fallback camera initialized", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Fallback camera also failed", e)
                    runOnUiThread {
                        Toast.makeText(this@ActivePatrolActivity, "Camera unavailable on this device", Toast.LENGTH_LONG).show()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Complete camera failure", e)
        }
    }

    private fun handleQrResult(qrData: String) {
        // Parse QR JSON
        val checkpoint = parseCheckpointFromQr(qrData) ?: run {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
            return
        }
        // Get Location
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            showConfirmationDialog(checkpoint, location)
        }
    }

    private fun showConfirmationDialog(checkpoint: QrCheckpoint, location: Location) {
        val message = "\uD83D\uDCCD <b>Checkpoint:</b> ${checkpoint.checkpointName}<br/>" +
                "<b>QR:</b> ${checkpoint.qrCodeData}<br/>" +
                "<b>Device Location:</b> ${location.latitude},${location.longitude}<br/>" +
                "<b>Checkpoint Location:</b> ${checkpoint.latitude},${checkpoint.longitude}"
        Log.d(TAG, "Showing confirmation dialog for checkpoint: $checkpoint, location: $location")
        AlertDialog.Builder(this)
            .setTitle("\uD83D\uDD0D Confirm Scan")
            .setMessage(android.text.Html.fromHtml(message))
            .setPositiveButton("\u2705 Confirm") { _, _ ->
                Log.d(TAG, "User confirmed scan. Calling scanCheckpoint...")
                scanCheckpoint(checkpoint, location)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scanCheckpoint(checkpoint: QrCheckpoint, location: Location) {
        val payload = ScanCheckpointPayload(
            qrCodeData = checkpoint.qrCodeData,
            latitudeAtScan = location.latitude,
            longitudeAtScan = location.longitude,
            notes = checkpoint.checkpointName
        )
        if (sessionId.isNullOrEmpty() || roleId.isNullOrEmpty()) {
            Log.e(TAG, "sessionId or roleId is null or empty! sessionId=$sessionId, roleId=$roleId")
            runOnUiThread {
                Toast.makeText(this@ActivePatrolActivity, "Session or role not set. Cannot scan.", Toast.LENGTH_LONG).show()
            }
            return
        }
        Log.d(TAG, "scanCheckpoint called with payload: $payload, sessionId=$sessionId, roleId=$roleId")
        val fullUrl = "${AppConfig.API_BASE_URL}employee/patrol/sessions/${sessionId}/scan?roleId=${roleId}"
        Log.d(TAG, "SCAN API URL: $fullUrl")
        Log.d(TAG, "Method: POST")
        Log.d(TAG, "Headers: Authorization: Bearer <redacted>, Accept: application/json, Content-Type: application/json")
        Log.d(TAG, "Request body: $payload")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, "Sending scan to server...", Toast.LENGTH_SHORT).show()
                }
                val api = Network.retrofit.create<PatrolApi>()
                val resp = api.scanCheckpoint(sessionId!!, roleId!!, payload)
                val rawError = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.d(TAG, "=== SCAN API RESPONSE ===")
                Log.d(TAG, "URL: $fullUrl")
                Log.d(TAG, "Status Code: ${resp.code()}")
                Log.d(TAG, "Body: ${resp.body()}")
                Log.d(TAG, "Error Body: $rawError")
                try {
                    val b = android.os.Bundle().apply {
                        putString("url", fullUrl)
                        putString("method", "POST")
                        putInt("status_code", resp.code())
                        putString("error", rawError)
                    }
                    Firebase.analytics.logEvent("patrol_scan", b)
                } catch (_: Exception) { }
                runOnUiThread {
                    if (resp.isSuccessful && resp.body()?.status == "success") {
                        Toast.makeText(this@ActivePatrolActivity, "Checkpoint scanned successfully!", Toast.LENGTH_LONG).show()
                        // Refresh status to determine if patrol can be ended
                        fetchPatrolStatus()
                        if (remainingCheckpointsCount == 0) {
                            AlertDialog.Builder(this@ActivePatrolActivity)
                                .setTitle("Patrol Complete")
                                .setMessage("All checkpoints are scanned. Do you want to end the patrol?")
                                .setPositiveButton("End Patrol") { _, _ -> confirmAndEndPatrol() }
                                .setNegativeButton("Continue", null)
                                .show()
                        } else {
                            // Allow scanning next checkpoint
                            startCamera()
                        }
                    } else {
                        val raw = rawError
                        Log.e(TAG, "Scan failed: code=${resp.code()} raw=${raw ?: "<empty>"}")
                        val errorMsg = try {
                            val obj = if (!raw.isNullOrEmpty()) org.json.JSONObject(raw) else null
                            obj?.optString("message") ?: (resp.body()?.message ?: "Unknown error. Please try again.")
                        } catch (_: Exception) {
                            resp.body()?.message ?: (raw ?: "Unknown error. Please try again.")
                        }

                        val isAlreadyScanned = errorMsg.contains("already been scanned", ignoreCase = true)
                        val builder = AlertDialog.Builder(this@ActivePatrolActivity)
                            .setTitle(if (isAlreadyScanned) "Already Scanned" else "Scan Failed")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK") { _, _ ->
                                // Allow re-scan by restarting camera/analyzer
                                startCamera()
                            }

                        if (isAlreadyScanned) {
                            builder.setNegativeButton("View Status") { _, _ ->
                                startActivity(android.content.Intent(this@ActivePatrolActivity, PatrolStatusActivity::class.java)
                                    .putExtra("sessionId", sessionId))
                                finish()
                            }
                        }
                        builder.show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in scanCheckpoint", e)
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, "Error: ${e.message ?: "Unknown error. Please try again."}", Toast.LENGTH_LONG).show()
                    // Restart camera to allow re-scanning after unexpected exceptions
                    startCamera()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // QR code parsing logic
    private fun parseCheckpointFromQr(qr: String): QrCheckpoint? {
        return try {
            val json = org.json.JSONObject(qr)
            QrCheckpoint(
                qrCodeData = json.getString("qr_code_data"),
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                checkpointName = json.getString("checkpoint_name")
            )
        } catch (e: Exception) {
            null
        }
    }

    data class QrCheckpoint(
        val qrCodeData: String,
        val latitude: Double,
        val longitude: Double,
        val checkpointName: String
    )

    inner class QRCodeAnalyzer(val onQrFound: (String) -> Unit) : ImageAnalysis.Analyzer {
        private var found = false
        private var frameCount = 0
        private var lastLogTime = 0L
        private var lastToastTime = 0L
        private var toastShown = false
        private val reader: MultiFormatReader = MultiFormatReader().apply {
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            setHints(hints)
        }

        override fun analyze(image: ImageProxy) {
            frameCount++
            val now = System.currentTimeMillis()

            if (found) {
                image.close()
                return
            }

            if (!toastShown && frameCount == 1) {
                showToastOnMainThread("Scanner initialized. Looking for QR code...")
                toastShown = true
            }

            if (now - lastLogTime > 2000) {
                android.util.Log.d(TAG, "QRCodeAnalyzer: Processing frame #$frameCount, size=${image.width}x${image.height}, rotation=${image.imageInfo.rotationDegrees}, format=${image.format}")
                lastLogTime = now
            }

            try {
                // Try multiple scanning approaches for better compatibility
                val results = mutableListOf<String>()
                
                // Approach 1: Direct plane processing
                try {
                    val result1 = scanWithDirectPlaneProcessing(image)
                    if (result1 != null) results.add(result1)
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Direct plane processing failed: ${e.message}")
                }
                
                // Approach 2: Bitmap conversion (more reliable but slower)
                try {
                    val result2 = scanWithBitmapConversion(image)
                    if (result2 != null) results.add(result2)
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Bitmap conversion failed: ${e.message}")
                }
                
                // Approach 3: Simple byte array processing
                try {
                    val result3 = scanWithSimpleByteArray(image)
                    if (result3 != null) results.add(result3)
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Simple byte array processing failed: ${e.message}")
                }
                
                // If any approach succeeded, use the result
                if (results.isNotEmpty()) {
                    val qrData = results.first()
                    android.util.Log.i(TAG, "QRCodeAnalyzer: SUCCESS! Found QR code: $qrData")
                    showToastOnMainThread("QR code detected!")
                    found = true
                    onQrFound(qrData)
                    reader.reset()
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                val errorType = e.javaClass.simpleName
                android.util.Log.e(TAG, "QRCodeAnalyzer: Error (frame #$frameCount) - Type: $errorType, Message: $errorMsg")
                
                if (frameCount < 5) { // Limit error toasts
                    showToastOnMainThread("Scan error: ${errorType.take(20)}")
                }
            } finally {
                image.close()
            }
        }
        
        private fun scanWithDirectPlaneProcessing(image: ImageProxy): String? {
            return try {
                android.util.Log.d(TAG, "Attempting direct plane processing for format ${image.format}")
                
                // Handle format 35 (YUV_420_888 variant on Iqoo devices)
                val luminanceSource = when (image.format) {
                    35, android.graphics.ImageFormat.YUV_420_888 -> {
                        android.util.Log.d(TAG, "Processing YUV format ${image.format}")
                        createRobustYUVLuminanceSource(image)
                    }
                    android.graphics.ImageFormat.NV21 -> {
                        android.util.Log.d(TAG, "Processing NV21 format")
                        createRobustNV21LuminanceSource(image)
                    }
                    else -> {
                        android.util.Log.d(TAG, "Processing unknown format ${image.format} with robust method")
                        createRobustGenericLuminanceSource(image)
                    }
                }

                if (luminanceSource != null) {
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
                    val result = reader.decodeWithState(binaryBitmap)
                    reader.reset()
                    android.util.Log.d(TAG, "Direct plane processing succeeded!")
                    return result.text
                }
                null
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Direct plane processing exception: ${e.message}")
                null
            }
        }
        
        private fun scanWithBitmapConversion(image: ImageProxy): String? {
            return try {
                android.util.Log.d(TAG, "Attempting bitmap conversion method")
                
                // Skip bitmap conversion for now as it's failing on this device
                // The HWUI decoder is not implemented on this device
                android.util.Log.d(TAG, "Skipping bitmap conversion due to device limitations")
                null
                
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Bitmap conversion failed: ${e.message}")
                null
            }
        }
        
        private fun scanWithSimpleByteArray(image: ImageProxy): String? {
            return try {
                android.util.Log.d(TAG, "Attempting simple byte array method")
                
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val bufferSize = buffer.remaining()
                
                android.util.Log.d(TAG, "Buffer size: $bufferSize, image size: ${image.width}x${image.height}")
                
                // Try different data extraction strategies
                val strategies = listOf(
                    // Strategy 1: Use actual image dimensions
                    Triple(image.width, image.height, image.width * image.height),
                    // Strategy 2: Use smaller dimensions for better compatibility
                    Triple(320, 240, 320 * 240),
                    // Strategy 3: Use square dimensions
                    Triple(240, 240, 240 * 240)
                )
                
                for ((width, height, expectedSize) in strategies) {
                    try {
                        val dataSize = minOf(bufferSize, expectedSize)
                        if (dataSize > 1000) { // Minimum reasonable size
                            buffer.rewind()
                            val data = ByteArray(dataSize)
                            buffer.get(data)
                            
                            val source = com.google.zxing.PlanarYUVLuminanceSource(
                                data, width, height, 0, 0, width, height, false
                            )
                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                            val result = reader.decodeWithState(binaryBitmap)
                            reader.reset()
                            
                            android.util.Log.d(TAG, "Simple byte array method succeeded with ${width}x${height}")
                            return result.text
                        }
                    } catch (e: Exception) {
                        android.util.Log.d(TAG, "Strategy ${width}x${height} failed: ${e.message}")
                        continue
                    }
                }
                
                null
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Simple byte array method failed: ${e.message}")
                null
            }
        }
        
        private fun imageProxyToBitmap(image: ImageProxy): android.graphics.Bitmap? {
            return try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to convert ImageProxy to Bitmap: ${e.message}")
                null
            }
        }

        private fun rotateYuv90or270(data: ByteArray, width: Int, height: Int, clockwise: Boolean): ByteArray {
            val out = ByteArray(data.size)
            var i = 0
            if (clockwise) {
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        out[i++] = data[y * width + x]
                    }
                }
            } else {
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        out[i++] = data[y * width + x]
                    }
                }
            }
            return out
        }

        private fun createRobustYUVLuminanceSource(image: ImageProxy): com.google.zxing.LuminanceSource? {
            return try {
                val yPlane = image.planes[0]
                val yBuffer = yPlane.buffer.duplicate() // Use duplicate to avoid buffer position issues
                
                val pixelStride = yPlane.pixelStride
                val rowStride = yPlane.rowStride
                val width = image.width
                val height = image.height
                
                android.util.Log.d(TAG, "YUV processing: ${width}x${height}, pixelStride=$pixelStride, rowStride=$rowStride, bufferSize=${yBuffer.remaining()}")
                
                // Create a clean byte array for luminance data
                val yData = ByteArray(width * height)
                
                if (pixelStride == 1) {
                    // Packed format - can copy row by row
                    for (row in 0 until height) {
                        val rowStart = row * rowStride
                        yBuffer.position(rowStart)
                        yBuffer.get(yData, row * width, width)
                    }
                } else {
                    // Interleaved format - copy pixel by pixel
                    yBuffer.rewind()
                    for (row in 0 until height) {
                        for (col in 0 until width) {
                            val bufferIndex = row * rowStride + col * pixelStride
                            if (bufferIndex < yBuffer.limit()) {
                                yData[row * width + col] = yBuffer.get(bufferIndex)
                            }
                        }
                    }
                }
                
                android.util.Log.d(TAG, "Successfully extracted ${yData.size} bytes of luminance data")
                com.google.zxing.PlanarYUVLuminanceSource(yData, width, height, 0, 0, width, height, false)
                
            } catch (e: Exception) {
                android.util.Log.w(TAG, "YUV luminance source creation failed: ${e.message}")
                null
            }
        }
        
        private fun createRobustNV21LuminanceSource(image: ImageProxy): com.google.zxing.LuminanceSource? {
            return try {
                val yPlane = image.planes[0]
                val yBuffer = yPlane.buffer.duplicate()
                val yData = ByteArray(yBuffer.remaining())
                yBuffer.get(yData)
                
                com.google.zxing.PlanarYUVLuminanceSource(yData, image.width, image.height, 0, 0, image.width, image.height, false)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "NV21 luminance source creation failed: ${e.message}")
                null
            }
        }
        
        private fun createRobustGenericLuminanceSource(image: ImageProxy): com.google.zxing.LuminanceSource? {
            return try {
                // Try to extract any available plane data
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val width = image.width
                val height = image.height
                
                // Calculate expected size
                val expectedSize = width * height
                val availableSize = buffer.remaining()
                
                android.util.Log.d(TAG, "Generic processing: expected=$expectedSize, available=$availableSize")
                
                if (availableSize >= expectedSize) {
                    // We have enough data
                    val data = ByteArray(expectedSize)
                    buffer.get(data)
                    com.google.zxing.PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
                } else {
                    // Not enough data, try with available data
                    val data = ByteArray(availableSize)
                    buffer.get(data)
                    val adjustedWidth = minOf(width, kotlin.math.sqrt(availableSize.toDouble()).toInt())
                    val adjustedHeight = minOf(height, availableSize / adjustedWidth)
                    
                    if (adjustedWidth > 0 && adjustedHeight > 0) {
                        com.google.zxing.PlanarYUVLuminanceSource(data, adjustedWidth, adjustedHeight, 0, 0, adjustedWidth, adjustedHeight, false)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Generic luminance source creation failed: ${e.message}")
                null
            }
        }
        
        private fun createLuminanceSourceFromYUV420(image: ImageProxy, rotation: Int): com.google.zxing.LuminanceSource {
            return createRobustYUVLuminanceSource(image) ?: throw RuntimeException("Failed to create YUV420 luminance source")
        }
        
        private fun createLuminanceSourceFromNV21(image: ImageProxy, rotation: Int): com.google.zxing.LuminanceSource {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            
            return com.google.zxing.PlanarYUVLuminanceSource(yBytes, image.width, image.height, 0, 0, image.width, image.height, false)
        }
        
        private fun createLuminanceSourceFromDeviceSpecific(image: ImageProxy, rotation: Int): com.google.zxing.LuminanceSource {
            // For Iqoo/Vivo devices with format 1, try multiple approaches
            try {
                // Approach 1: Try as YUV with stride handling
                val yPlane = image.planes[0]
                val yBuffer = yPlane.buffer
                val ySize = yBuffer.remaining()
                val pixelStride = yPlane.pixelStride
                val rowStride = yPlane.rowStride
                
                android.util.Log.d(TAG, "Device format 1: size=$ySize, pixelStride=$pixelStride, rowStride=$rowStride")
                
                if (pixelStride == 1 && rowStride == image.width) {
                    // Simple case - direct copy
                    val yBytes = ByteArray(ySize)
                    yBuffer.get(yBytes)
                    
                    val (data, w, h) = if (rotation == 90 || rotation == 270) {
                        val rotated = rotateYuv90or270(yBytes, image.width, image.height, rotation == 90)
                        Triple(rotated, image.height, image.width)
                    } else {
                        Triple(yBytes, image.width, image.height)
                    }
                    
                    return com.google.zxing.PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
                } else {
                    // Complex case - handle stride
                    val yBytes = ByteArray(image.width * image.height)
                    var outputOffset = 0
                    
                    for (y in 0 until image.height) {
                        val inputOffset = y * rowStride
                        yBuffer.position(inputOffset)
                        
                        if (pixelStride == 1) {
                            // Packed pixels
                            yBuffer.get(yBytes, outputOffset, image.width)
                            outputOffset += image.width
                        } else {
                            // Interleaved pixels
                            for (x in 0 until image.width) {
                                yBytes[outputOffset++] = yBuffer.get(inputOffset + x * pixelStride)
                            }
                        }
                    }
                    
                    val (data, w, h) = if (rotation == 90 || rotation == 270) {
                        val rotated = rotateYuv90or270(yBytes, image.width, image.height, rotation == 90)
                        Triple(rotated, image.height, image.width)
                    } else {
                        Triple(yBytes, image.width, image.height)
                    }
                    
                    return com.google.zxing.PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Device-specific format processing failed, using generic fallback: ${e.message}")
                return createLuminanceSourceFromGeneric(image, rotation)
            }
        }
        
        private fun createLuminanceSourceFromGeneric(image: ImageProxy, rotation: Int): com.google.zxing.LuminanceSource {
            // Generic fallback - try to extract luminance data
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yBytes = ByteArray(minOf(yBuffer.remaining(), image.width * image.height))
            yBuffer.get(yBytes)
            
            // Ensure we have enough data
            val actualWidth = minOf(image.width, kotlin.math.sqrt(yBytes.size.toDouble()).toInt())
            val actualHeight = minOf(image.height, yBytes.size / actualWidth)
            
            return com.google.zxing.PlanarYUVLuminanceSource(yBytes, actualWidth, actualHeight, 0, 0, actualWidth, actualHeight, false)
        }

        private fun showToastOnMainThread(message: String) {
            val now = System.currentTimeMillis()
            if (now - lastToastTime > 2000) {
                lastToastTime = now
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showScanError(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanErrorAt < 1500) return
        lastScanErrorAt = now
        runOnUiThread {
            Toast.makeText(this@ActivePatrolActivity, message.take(120), Toast.LENGTH_SHORT).show()
        }
    }
    
    // Add a test button for manual QR input (for debugging)
    private fun addTestQrButton() {
        val testButton = android.widget.Button(this)
        testButton.text = "Test QR"
        testButton.setOnClickListener {
            // Simulate a QR code scan for testing
            val testQrData = """{"qr_code_data":"TEST123","latitude":12.9716,"longitude":77.5946,"checkpoint_name":"Test Checkpoint"}"""
            handleQrResult(testQrData)
        }
        
        // Add button to layout (you can remove this in production)
        val layout = findViewById<android.widget.LinearLayout>(R.id.container) // Adjust ID as needed
        layout?.addView(testButton)
    }
}