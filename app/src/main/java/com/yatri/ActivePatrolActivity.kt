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
import com.yatri.patrol.EndPatrolBody
import com.yatri.patrol.ScanCheckpointPayload
import com.yatri.net.Network
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_patrol)
        previewView = findViewById(R.id.previewView)
        sessionId = intent.getStringExtra("sessionId")
        roleId = intent.getStringExtra("roleId")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        requestPermissions()
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                val resp = api.scanCheckpoint(sessionId!!, roleId!!, payload)
                Log.d(TAG, "scanCheckpoint API response: $resp, body: ${resp.body()} raw: ${resp.errorBody()?.string()}")
                runOnUiThread {
                    if (resp.isSuccessful && resp.body()?.status == "success") {
                        Toast.makeText(this@ActivePatrolActivity, "Checkpoint scanned successfully!", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Log.e(TAG, "Scan failed: ${resp.body()?.message}, errorBody: ${resp.errorBody()?.string()}")
                        val errorMsg = resp.body()?.message
                            ?: resp.errorBody()?.string()?.let {
                                try {
                                    val obj = org.json.JSONObject(it)
                                    obj.optString("message", it)
                                } catch (e: Exception) { it }
                            }
                            ?: "Unknown error. Please try again."
                        AlertDialog.Builder(this@ActivePatrolActivity)
                            .setTitle("Scan Failed")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in scanCheckpoint", e)
                runOnUiThread {
                    Toast.makeText(this@ActivePatrolActivity, "Error: ${e.message ?: "Unknown error. Please try again."}", Toast.LENGTH_LONG).show()
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