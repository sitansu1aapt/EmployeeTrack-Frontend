package com.yatri

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.yatri.net.Network
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

class ProfileFragment : Fragment() {
    private val profileApi = Network.retrofit.create(ProfileApi::class.java)
    private var ivProfilePhoto: ImageView? = null
    private var currentProfilePictureUrl: String? = null

    // Activity result launcher for gallery image picking
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handleImageSelected(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto)

        // Camera badge click → open gallery/camera picker
        view.findViewById<ImageView>(R.id.ivEditPhoto)?.setOnClickListener {
            openImagePicker()
        }
        // Also allow clicking the photo itself
        ivProfilePhoto?.setOnClickListener {
            openImagePicker()
        }

        // Load profile data from API
        loadProfileData(view)

        // Background tracking
        view.findViewById<Switch>(R.id.swBackground)?.setOnCheckedChangeListener { _, isChecked ->
            val ctx = requireContext()
            if (isChecked) ctx.startService(Intent(ctx, LocationService::class.java)) else ctx.stopService(Intent(ctx, LocationService::class.java))
        }
        // Change password (placeholder)
        view.findViewById<Button>(R.id.btnChangePassword)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Change Password: Coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        // Language selector
        view.findViewById<Spinner>(R.id.spinnerLanguage)?.let { spinner ->
            val items = listOf("English" to "en", "Odia" to "or")
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items.map { it.first })
            lifecycleScope.launch {
                val current = requireContext().dataStore.data.first()[PrefKeys.LANGUAGE] ?: "en"
                val idx = items.indexOfFirst { it.second == current }.coerceAtLeast(0)
                spinner.setSelection(idx)
            }
            spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, position: Int, id: Long) {
                    val code = items[position].second
                    lifecycleScope.launch { requireContext().dataStore.edit { it[PrefKeys.LANGUAGE] = code } }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            })
        }
        // Update FCM
        view.findViewById<Button>(R.id.btnUpdateFcm)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Update FCM Token: Coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        // Test Notification row
        view.findViewById<View>(R.id.rowTestNotification)?.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationTestActivity::class.java))
        }
        // Release Notes
        view.findViewById<Button>(R.id.btnReleaseNotes)?.setOnClickListener {
            startActivity(Intent(requireContext(), ReleaseNotesActivity::class.java))
        }
        // Logout
        view.findViewById<Button>(R.id.btnLogout)?.setOnClickListener {
            lifecycleScope.launch {
                requireContext().dataStore.edit { it.clear() }
                TokenStore.token = null
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
        }

        // Bind header placeholders from DataStore (fallback)
        lifecycleScope.launch {
            val prefs = requireContext().dataStore.data.first()
            val name = prefs[PrefKeys.USER_NAME] ?: "test user"
            val role = prefs[PrefKeys.ACTIVE_ROLE_NAME] ?: "EMPLOYEE"
            view.findViewById<TextView>(R.id.tvName)?.text = name
            view.findViewById<TextView>(R.id.tvRole)?.text = role
            val versionName = try {
                val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                pInfo.versionName ?: "1.0"
            } catch (e: Exception) { "1.0" }
            view.findViewById<TextView>(R.id.tvVersion)?.text = "v$versionName"
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        pickImageLauncher.launch(Intent.createChooser(intent, "Select Profile Photo"))
    }

    private fun handleImageSelected(uri: Uri) {
        // Show selected image immediately (optimistic UI)
        ivProfilePhoto?.setImageURI(uri)

        // Upload to backend
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inputStream: InputStream = requireContext().contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("profilePhoto", "profile_${System.currentTimeMillis()}.jpg", requestBody)

                showToast("Uploading photo...")

                val uploadResp = profileApi.uploadProfilePhoto(part)
                if (uploadResp.isSuccessful && uploadResp.body()?.success == true) {
                    val photoUrl = uploadResp.body()!!.data.profile_photo_url
                    android.util.Log.d("ProfileFragment", "Photo uploaded: $photoUrl")

                    // Now save the URL to the user profile
                    val updateResp = profileApi.updateProfilePicture(UpdateProfileBody(photoUrl))
                    if (updateResp.isSuccessful && updateResp.body()?.success == true) {
                        currentProfilePictureUrl = photoUrl
                        showToast("Profile photo updated successfully!")
                        android.util.Log.d("ProfileFragment", "Profile picture URL saved to user record")
                    } else {
                        android.util.Log.e("ProfileFragment", "Failed to save URL to profile: ${updateResp.code()}")
                        showToast("Photo uploaded but profile update failed. Please try again.")
                    }
                } else {
                    android.util.Log.e("ProfileFragment", "Upload failed: ${uploadResp.code()} - ${uploadResp.message()}")
                    showToast("Upload failed. Please try again.")
                    // Revert image on failure
                    reloadProfilePhoto()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error uploading photo", e)
                showToast("Upload error: ${e.message}")
                reloadProfilePhoto()
            }
        }
    }

    private fun reloadProfilePhoto() {
        val url = currentProfilePictureUrl
        if (!url.isNullOrBlank()) {
            loadImageFromUrl(url)
        } else {
            ivProfilePhoto?.setImageResource(R.drawable.ic_person_placeholder)
        }
    }

    private fun loadImageFromUrl(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    bmp
                }
                if (bmp != null) {
                    ivProfilePhoto?.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error loading profile image", e)
            }
        }
    }

    private fun showToast(msg: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfileData(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = profileApi.getProfile()

                if (response.isSuccessful) {
                    val profileData = response.body()
                    if (profileData?.success == true) {
                        val user = profileData.data.user

                        view.findViewById<TextView>(R.id.tvName)?.text = user.full_name
                        view.findViewById<TextView>(R.id.tvRole)?.text = user.activeContext.roleName
                        view.findViewById<TextView>(R.id.tvUserId)?.text = "id: ${user.user_id}"
                        view.findViewById<TextView>(R.id.tvEmail)?.text = user.email
                        view.findViewById<TextView>(R.id.tvDepartment)?.text = user.department_id ?: "Not Assigned"
                        view.findViewById<TextView>(R.id.tvOrganization)?.text =
                            "${user.activeContext.roleName} @ OrgID ${user.organization_id}"

                        // Load profile picture if available
                        val picUrl = user.profile_picture_url
                        currentProfilePictureUrl = picUrl
                        if (!picUrl.isNullOrBlank()) {
                            loadImageFromUrl(picUrl)
                        }

                        // Update DataStore
                        requireContext().dataStore.edit {
                            it[PrefKeys.USER_NAME] = user.full_name
                            it[PrefKeys.ACTIVE_ROLE_ID] = profileData.data.auth.roleId
                            it[PrefKeys.ACTIVE_ROLE_NAME] = user.activeContext.roleName
                            it[PrefKeys.ORG_ID] = user.organization_id
                            it[PrefKeys.SITE_ID] = user.activeContext.scopedSiteId ?: ""
                            it[PrefKeys.DEPT_ID] = user.department_id ?: ""
                            it[PrefKeys.USER_ID] = user.user_id
                            it[PrefKeys.USER_EMAIL] = user.email
                        }
                        com.yatri.UserContext.userId = user.user_id
                        com.yatri.UserContext.userEmail = user.email
                        com.yatri.UserContext.userName = user.full_name
                        com.yatri.UserContext.roleName = user.activeContext.roleName
                        com.yatri.UserContext.orgId = user.organization_id
                        com.yatri.UserContext.siteId = user.activeContext.scopedSiteId
                        com.yatri.UserContext.deptId = user.department_id
                    }
                } else {
                    android.util.Log.e("ProfileFragment", "Profile API failed: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error loading profile", e)
            }
        }
    }
}
