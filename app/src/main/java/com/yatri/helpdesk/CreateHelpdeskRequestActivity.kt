package com.yatri.helpdesk

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.yatri.PrefKeys
import com.yatri.ProfileApi
import com.yatri.R
import com.yatri.dataStore
import com.yatri.net.Network
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class CreateHelpdeskRequestActivity : AppCompatActivity() {
    private lateinit var tvEmployeeName: TextView
    private lateinit var tvEmployeeId: TextView
    private lateinit var spinnerQueryType: Spinner
    private lateinit var etDescription: EditText
    private lateinit var btnAttach: Button
    private lateinit var btnSubmit: Button
    private lateinit var tvAttachment: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRemoveAttachment: ImageButton
    private lateinit var attachmentRow: View

    private var attachmentUri: Uri? = null
    private var attachmentMime: String? = null
    private var attachmentName: String? = null

    private val pickAttachment = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentUri = uri
            attachmentName = queryFileName(uri)
            attachmentMime = contentResolver.getType(uri)
            tvAttachment.text = attachmentName ?: uri.lastPathSegment ?: "Selected file"
            attachmentRow.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_helpdesk_request)

        tvEmployeeName = findViewById(R.id.tvEmployeeName)
        tvEmployeeId = findViewById(R.id.tvEmployeeId)
        spinnerQueryType = findViewById(R.id.spinnerQueryType)
        etDescription = findViewById(R.id.etDescription)
        btnAttach = findViewById(R.id.btnAttachFile)
        btnSubmit = findViewById(R.id.btnSubmitRequest)
        tvAttachment = findViewById(R.id.tvAttachmentName)
        progressBar = findViewById(R.id.progressBar)
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment)
        attachmentRow = findViewById(R.id.attachmentRow)

        attachmentRow.visibility = View.GONE

        spinnerQueryType.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.select_query_type), "MOBILE_APPLICATION", "UNIFORM", "HR", "SALARY", "OPERATIONS", "OTHER")
        )

        populateEmployeeHeader()

        btnAttach.setOnClickListener {
            pickAttachment.launch("*/*")
        }

        btnRemoveAttachment.setOnClickListener {
            attachmentUri = null
            attachmentMime = null
            attachmentName = null
            attachmentRow.visibility = View.GONE
        }

        btnSubmit.setOnClickListener { submitRequest() }
    }

    private fun populateEmployeeHeader() {
        lifecycleScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            bindEmployeeDetails(
                name = prefs[PrefKeys.USER_NAME],
                employeeId = prefs[PrefKeys.USER_EMPLOYEE_ID]
            )
        }

        lifecycleScope.launch {
            runCatching {
                val api = Network.retrofit.create(ProfileApi::class.java)
                api.getProfile()
            }.onSuccess { response ->
                val profile = response.body()?.takeIf { response.isSuccessful && it.success }?.data?.user ?: return@onSuccess
                bindEmployeeDetails(profile.full_name, profile.employee_id)
                applicationContext.dataStore.edit {
                    it[PrefKeys.USER_NAME] = profile.full_name
                    it[PrefKeys.USER_EMPLOYEE_ID] = profile.employee_id
                }
            }.onFailure { error ->
                android.util.Log.w("CreateHelpdesk", "Unable to refresh employee header", error)
            }
        }
    }

    private fun bindEmployeeDetails(name: String?, employeeId: String?) {
        tvEmployeeName.text = name?.takeIf { it.isNotBlank() } ?: getString(R.string.not_available)
        tvEmployeeId.text = employeeId?.takeIf { it.isNotBlank() } ?: getString(R.string.not_available)
    }

    private fun submitRequest() {
        val queryType = spinnerQueryType.selectedItem?.toString() ?: ""
        val description = etDescription.text.toString().trim()

        if (queryType == getString(R.string.select_query_type) || queryType.isBlank()) {
            showToast(getString(R.string.please_select_query_type))
            return
        }
        if (description.isBlank()) {
            showToast(getString(R.string.please_enter_description))
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                val attachmentResponse = if (attachmentUri != null) {
                    uploadAttachment(attachmentUri!!)
                } else null

                val body = CreateHelpdeskRequestBody(
                    query_type = queryType,
                    description = description,
                    attachment_url = attachmentResponse?.attachment_url,
                    attachment_file_name = attachmentResponse?.attachment_file_name,
                    attachment_mime_type = attachmentResponse?.attachment_mime_type
                )
                val api = Network.retrofit.create(HelpdeskApi::class.java)
                val response = api.createRequest(body)
                if (response.success) {
                    showToast(getString(R.string.helpdesk_request_created))
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    showToast(response.message.ifEmpty { getString(R.string.unable_to_create_request) })
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateHelpdesk", "submitRequest error", e)
                showToast(getString(R.string.error_occurred))
            } finally {
                progressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
            }
        }
    }

    private suspend fun uploadAttachment(uri: Uri): HelpdeskAttachment? {
        return try {
            val resolver: ContentResolver = contentResolver
            val mimeType = attachmentMime ?: resolver.getType(uri) ?: "application/octet-stream"
            val name = attachmentName ?: queryFileName(uri) ?: "attachment"
            val input = resolver.openInputStream(uri) ?: return null
            val bytes = input.readBytes().also { input.close() }
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, bytes.size)
            val part = MultipartBody.Part.createFormData("attachment", name, requestBody)
            val api = Network.retrofit.create(HelpdeskApi::class.java)
            val response = api.uploadAttachment(part)
            if (response.success) response.data else throw Exception(response.message)
        } catch (e: Exception) {
            android.util.Log.e("CreateHelpdesk", "uploadAttachment error", e)
            showToast(getString(R.string.attachment_upload_failed))
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
