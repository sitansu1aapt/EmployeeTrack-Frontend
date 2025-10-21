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
import android.view.View
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

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions

class CheckInActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private var geofencePolygon: List<LatLng> = listOf()
    private var userLatLng: LatLng? = null
    private var step = 0
    private var lat: Double? = null
    private var lng: Double? = null
    private var acc: Double? = null
    private var selfieUrl: String? = null
    private var photoFile: File? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var hasSelfie: Boolean = false

    private val requestCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            compressPhoto()
            uploadSelfie()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkin_multistep)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Fetch site assignment and location from API
        val retrofit = com.yatri.net.Network.retrofit
        val api = retrofit.create(com.yatri.checkin.SiteAssignmentApi::class.java)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val response = api.getSiteAssignmentsByUserId()
                val assignments = response.body()?.data
                val status = response.body()?.status
                android.util.Log.d("CheckInActivity", "API status: $status, assignments: $assignments")
                if (status == "success" && !assignments.isNullOrEmpty()) {
                    val assignment = assignments[0]
                    geofencePolygon = assignment.geofenceShapeData.coordinates.map { com.google.android.gms.maps.model.LatLng(it.lat, it.lng) }
                    android.util.Log.d("CheckInActivity", "Geofence polygon: ${assignment.geofenceShapeData.coordinates}")
                } else {
                    android.util.Log.e("CheckInActivity", "No site assignments found or invalid response")
                }
            } catch (e: Exception) {
                android.util.Log.e("CheckInActivity", "Error fetching site assignments", e)
            }
            // Fetch user location
            val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this@CheckInActivity)
            if (androidx.core.content.ContextCompat.checkSelfPermission(this@CheckInActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLatLng = com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude)
                        renderStep()
                        updateGeofenceUI()
                        mapFragment.getMapAsync(this@CheckInActivity)
                    }
                }
            }
        }
        renderStep()

        val btnTakeSelfie = findViewById<Button>(R.id.btnTakeSelfie)
        val ivSelfiePreview = findViewById<ImageView>(R.id.ivSelfiePreview)
        btnTakeSelfie.setOnClickListener {
            capturePhoto()
        }

        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext.setOnClickListener {
            android.util.Log.i("CheckInActivity", "btnNext clicked: step=$step enabled=${it.isEnabled} visibility=${it.visibility}")
            if (!it.isEnabled) {
                android.util.Log.w("CheckInActivity", "btnNext click ignored because isEnabled=false")
                return@setOnClickListener
            }
            when (step) {
                0 -> {
                    android.util.Log.d("CheckInActivity", "Action: fetchLocation()")
                    fetchLocation()
                }
                1 -> {
                    if (hasSelfie) {
                        android.util.Log.d("CheckInActivity", "Proceeding from selfie step to notes")
                        step++
                    } else {
                        Toast.makeText(this, "Please take a selfie to continue", Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> {
                    android.util.Log.d("CheckInActivity", "Action: increment step from 2 to 3")
                    step++
                }
                3 -> {
                    android.util.Log.d("CheckInActivity", "Action: submit()")
                    submit()
                }
                else -> android.util.Log.w("CheckInActivity", "Unexpected step=$step")
            }
            android.util.Log.i("CheckInActivity", "After click handled -> step=$step")
            renderStep()
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            if (step > 0) step--
            renderStep()
        }
    }

    private fun checkGeofence(): Boolean {
        // Use ray-casting algorithm
        val user = userLatLng ?: return false
        if (geofencePolygon.size < 3) return false
        var intersectCount = 0
        for (j in geofencePolygon.indices) {
            val k = (j + 1) % geofencePolygon.size
            val a = geofencePolygon[j]
            val b = geofencePolygon[k]
            if (((a.latitude > user.latitude) != (b.latitude > user.latitude)) &&
                (user.longitude < (b.longitude - a.longitude) * (user.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude)
            ) {
                intersectCount++
            }
        }
        return (intersectCount % 2 == 1)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Draw marker
        userLatLng?.let { userLoc ->
            map.addMarker(MarkerOptions().position(userLoc).title("You"))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLoc, 16f))
        }
        // Draw polygon
        if (geofencePolygon.isNotEmpty()) {
            map.addPolygon(
                PolygonOptions()
                    .addAll(geofencePolygon)
                    .strokeColor(ContextCompat.getColor(this, R.color.primary))
                    .fillColor(ContextCompat.getColor(this, R.color.primary) and 0x33FFFFFF)
                    .strokeWidth(3f)
            )
        }
        updateGeofenceUI()
    }

    private fun updateGeofenceUI() {
        val inside = checkGeofence()
        // Update status box and button here as in your renderStep()
        val geofenceStatus = findViewById<TextView>(R.id.tvGeofenceStatus)
        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext.alpha = 1.0f
        geofenceStatus.visibility = View.VISIBLE
        if (inside) {
            geofenceStatus.text = "You are within the site boundary"
            geofenceStatus.setTextColor(android.graphics.Color.parseColor("#0F5132"))
            geofenceStatus.setBackgroundResource(R.drawable.confirmation_box_bg)
            btnNext.isEnabled = true
        } else {
            geofenceStatus.text = "You must be within the site boundary to check in"
            geofenceStatus.setTextColor(android.graphics.Color.parseColor("#B71C1C"))
            geofenceStatus.setBackgroundResource(R.drawable.geofence_status_error)
            btnNext.isEnabled = false
        }
    }

    private fun renderStep() {
        val stepDots: List<View> = listOf(
            findViewById(R.id.stepDot0),
            findViewById(R.id.stepDot1),
            findViewById(R.id.stepDot2)
        )
        for (i in stepDots.indices) {
            when {
                i < step -> stepDots[i].setBackgroundResource(R.drawable.step_dot_completed)
                i == step -> stepDots[i].setBackgroundResource(R.drawable.step_dot_active)
                else -> stepDots[i].setBackgroundResource(R.drawable.step_dot_inactive)
            }
        }
        val locationStep = findViewById<android.widget.LinearLayout>(R.id.locationStepContent)
        val selfieStep = findViewById<android.widget.LinearLayout>(R.id.selfieStepContent)
        val notesStep = findViewById<android.widget.LinearLayout>(R.id.notesStepContent)
        locationStep.visibility = if (step == 0) View.VISIBLE else View.GONE
        selfieStep.visibility = if (step == 1) View.VISIBLE else View.GONE
        notesStep.visibility = if (step == 2) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnPrev).visibility = if (step == 0) View.GONE else View.VISIBLE
        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext.text = when (step) {
            2 -> "Complete"
            else -> "Continue"
        }
        btnNext.setTextColor(resources.getColor(android.R.color.white, theme))
        btnNext.textSize = 16f
        btnNext.isAllCaps = false
        // Use default typeface or Typeface.DEFAULT_BOLD for bold text
        btnNext.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        // Keep the button fully opaque always; only toggle isEnabled
        btnNext.alpha = 1.0f
        // Geofence status styling
        val geofenceStatus = findViewById<TextView>(R.id.tvGeofenceStatus)
        if (step == 0) {
            val withinGeofence = checkGeofence() // Implement this function to check geofence logic
            geofenceStatus.visibility = View.VISIBLE
            if (withinGeofence) {
                geofenceStatus.text = "You are within the site boundary"
                geofenceStatus.setTextColor(android.graphics.Color.parseColor("#0F5132"))
                geofenceStatus.setBackgroundResource(R.drawable.confirmation_box_bg)
                btnNext.isEnabled = true
                btnNext.text =  "Continue"
            } else {
                geofenceStatus.text = "You must be within the site boundary to check in"
                geofenceStatus.setTextColor(android.graphics.Color.parseColor("#B71C1C"))
                geofenceStatus.setBackgroundResource(R.drawable.geofence_status_error)
                btnNext.isEnabled = false
            }
        } else {
            // Enable Continue on selfie step only after selfie is captured/uploaded
            btnNext.isEnabled = if (step == 1) hasSelfie else true
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
        findViewById<ImageView>(R.id.ivSelfiePreview).setImageBitmap(scaled)
        hasSelfie = true
        runOnUiThread { renderStep() }
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
                    hasSelfie = true
                    runOnUiThread {
                        Toast.makeText(this@CheckInActivity, "Selfie uploaded", Toast.LENGTH_SHORT).show()
                        renderStep()
                    }
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


