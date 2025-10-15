package com.yatri.checkin

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yatri.R

class CheckInSelfieActivity : AppCompatActivity() {
    private lateinit var imgSelfie: ImageView
    private lateinit var btnTakeSelfie: Button
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button
    private lateinit var etNotes: EditText
    private var selfieBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_selfie)
        imgSelfie = findViewById(R.id.imgSelfiePreview)
        btnTakeSelfie = findViewById(R.id.btnTakeSelfie)
        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)
        etNotes = findViewById(R.id.etNotes)

        btnTakeSelfie.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, 1001)
        }
        btnBack.setOnClickListener { finish() }
        btnNext.setOnClickListener {
            if (selfieBitmap == null) {
                Toast.makeText(this, "Please take a selfie to continue", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: Pass selfie and notes to next step (submit)
                Toast.makeText(this, "Proceeding to submit step...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                selfieBitmap = bitmap
                imgSelfie.setImageBitmap(bitmap)
            }
        }
    }
}
