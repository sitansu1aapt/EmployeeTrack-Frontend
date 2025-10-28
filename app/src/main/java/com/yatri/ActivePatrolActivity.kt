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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_patrol)
        previewView = findViewById(R.id.previewView)
        sessionId = intent.getStringExtra("sessionId")
        roleId = intent.getStringExtra("roleId")
        Log.d(TAG, "onCreate - ActivePatrolActivity opened. sessionId=$sessionId roleId=$roleId routeName=${intent.getStringExtra("routeName")}")
        // Bind route name and tentative status from intent (for fast UI)
        intent.getStringExtra("routeName")?.let { findViewById<TextView>(R.id.tvRouteName).text = it }
        findViewById<TextView>(R.id.tvStatus).text = "Status: IN_PROGRESS"
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        // Wire up End Patrol button
        findViewById<Button>(R.id.btnEndPatrol).setOnClickListener {
            confirmAndEndPatrol()
        }
        requestPermissions()
        // Initial status fetch to update UI
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
        if (missing.isEmpty()) {
            startCamera()
        } else {
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
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrData ->
                    runOnUiThread { handleQrResult(qrData) }
                })
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
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
                val api = Network.retrofit.create<PatrolApi>()
                val resp = api.scanCheckpoint(sessionId!!, roleId!!, payload)
                val rawError = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                Log.d(TAG, "=== SCAN API RESPONSE ===")
                Log.d(TAG, "URL: $fullUrl")
                Log.d(TAG, "Status Code: ${resp.code()}")
                Log.d(TAG, "Body: ${resp.body()}")
                Log.d(TAG, "Error Body: $rawError")
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

    class QRCodeAnalyzer(val onQrFound: (String) -> Unit) : ImageAnalysis.Analyzer {
        private var found = false
        override fun analyze(image: ImageProxy) {
            if (found) {
                image.close()
                return
            }
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val source = com.google.zxing.PlanarYUVLuminanceSource(
                bytes, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = MultiFormatReader().decode(binaryBitmap)
                found = true
                onQrFound(result.text)
            } catch (_: Exception) { }
            image.close()
        }
    }
}