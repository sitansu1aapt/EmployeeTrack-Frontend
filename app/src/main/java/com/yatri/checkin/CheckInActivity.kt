package com.yatri.checkin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.yatri.R
import com.yatri.dataStore
import com.yatri.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.create
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckInActivity : AppCompatActivity() {
    private var step = 0
    private var lat: Double? = null
    private var lng: Double? = null
    private var acc: Double? = null
    private var selfieUrl: String? = null
    private var photoFile: File? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val requestCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            compressPhoto()
            uploadSelfie()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin)

        renderStep()

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            when (step) {
                0 -> fetchLocation()
                1 -> capturePhoto()
                2 -> step++
                3 -> submit()
            }
            renderStep()
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            if (step > 0) step--
            renderStep()
        }
    }

    private fun renderStep() {
        findViewById<TextView>(R.id.tvStep).text = when (step) {
            0 -> "Step 1: Location"
            1 -> "Step 2: Selfie"
            2 -> "Step 3: Notes"
            else -> "Step 4: Confirm"
        }

        findViewById<TextView>(R.id.tvStatus).text = when (step) {
            0 -> "Get current location"
            1 -> if (selfieUrl == null) "Capture and upload selfie" else "Selfie uploaded"
            2 -> "Add optional notes"
            else -> "Review and submit"
        }
    }

    private fun fetchLocation() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                lat = loc.latitude
                lng = loc.longitude
                acc = loc.accuracy.toDouble()
                step++
                renderStep()
            } else {
                Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Location failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun capturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        try {
            val dir = File(cacheDir, "images").apply { mkdirs() }
            photoFile = File(dir, "selfie.jpg")
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile!!)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            requestCamera.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressPhoto() {
        val file = photoFile ?: return
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        val targetW = 720
        val ratio = bmp.width.toFloat() / bmp.height
        val targetH = (targetW / ratio).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        findViewById<ImageView>(R.id.ivSelfie).setImageBitmap(scaled)
    }

    private fun uploadSelfie() {
        val file = photoFile ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val filesApi = Network.retrofit.create<FilesApi>()
                val body = file.asRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("photo", file.name, body)
                val resp = filesApi.uploadAttendanceSelfie(part)
                val url = resp.data?.selfie_url
                if (!url.isNullOrEmpty()) {
                    selfieUrl = url
                    runOnUiThread { Toast.makeText(this@CheckInActivity, "Selfie uploaded", Toast.LENGTH_SHORT).show() }
                    step++
                    runOnUiThread { renderStep() }
                } else {
                    runOnUiThread { Toast.makeText(this@CheckInActivity, "Upload failed", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@CheckInActivity, e.message ?: "Upload error", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun submit() {
        val latitude = lat
        val longitude = lng
        val accuracy = acc
        val selfie = selfieUrl
        if (latitude == null || longitude == null || accuracy == null || selfie == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = intent.getStringExtra("mode") ?: "checkin"
        val notes = findViewById<EditText>(R.id.etNotes).text?.toString()?.trim().orEmpty()
        val device = DeviceInfo(
            device_model = Build.MODEL ?: "Android",
            platform = "Android",
            platform_version = Build.VERSION.RELEASE ?: ""
        )
        val payload = CheckInData(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            check_in_time = isoNow(),
            selfie_url = selfie,
            device_info = device,
            notes = notes.ifEmpty { null }
        )
        scope.launch(Dispatchers.IO) {
            try {
                val api = Network.retrofit.create<AttendanceApi>()
                if (mode == "checkout") api.checkOut(payload) else api.checkIn(payload)
                runOnUiThread {
                    Toast.makeText(this@CheckInActivity, "Submitted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@CheckInActivity, e.message ?: "Submit failed", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
}


