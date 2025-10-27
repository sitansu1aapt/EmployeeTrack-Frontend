package com.yatri

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.yatri.net.Network

class ProfileFragment : Fragment() {
    private val profileApi = Network.retrofit.create(ProfileApi::class.java)
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
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
        // Logout
        view.findViewById<Button>(R.id.btnLogout)?.setOnClickListener {
            lifecycleScope.launch {
                // Clear DataStore preferences
                requireContext().dataStore.edit { it.clear() }
                // Clear TokenStore
                TokenStore.token = null
                android.util.Log.d("ProfileFragment", "User logged out - cleared all data")
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
        }

        // Bind header placeholders (from DataStore if present) - fallback only
        lifecycleScope.launch {
            val prefs = requireContext().dataStore.data.first()
            val name = prefs[PrefKeys.USER_NAME] ?: "test user"
            val role = prefs[PrefKeys.ACTIVE_ROLE_NAME] ?: "EMPLOYEE"
            view.findViewById<TextView>(R.id.tvName)?.text = name
            view.findViewById<TextView>(R.id.tvRole)?.text = role
        }
    }
    
    private fun loadProfileData(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profileUrl = "${AppConfig.API_BASE_URL}auth/me"
                android.util.Log.d("ProfileFragment", "=== LOADING PROFILE ===")
                android.util.Log.d("ProfileFragment", "URL: $profileUrl")
                android.util.Log.d("ProfileFragment", "Token: ${TokenStore.token?.take(30)}...")
                
                val response = profileApi.getProfile()
                
                if (response.isSuccessful) {
                    val profileData = response.body()
                    if (profileData?.success == true) {
                        android.util.Log.d("ProfileFragment", "Profile loaded successfully")
                        android.util.Log.d("ProfileFragment", "User: ${profileData.data.user.full_name}")
                        android.util.Log.d("ProfileFragment", "Email: ${profileData.data.user.email}")
                        android.util.Log.d("ProfileFragment", "Role: ${profileData.data.user.activeContext.roleName}")
                        
                        // Update UI with real data
                        view.findViewById<TextView>(R.id.tvName)?.text = profileData.data.user.full_name
                        view.findViewById<TextView>(R.id.tvRole)?.text = profileData.data.user.activeContext.roleName
                        view.findViewById<TextView>(R.id.tvUserId)?.text = "id: ${profileData.data.user.user_id}"
                        view.findViewById<TextView>(R.id.tvEmail)?.text = profileData.data.user.email
                        
                        // Update department if available
                        val department = profileData.data.user.department_id
                        view.findViewById<TextView>(R.id.tvDepartment)?.text = department ?: "Not Assigned"
                        
                        // Update organization
                        view.findViewById<TextView>(R.id.tvOrganization)?.text = 
                            "${profileData.data.user.activeContext.roleName} @ OrgID ${profileData.data.user.organization_id}"
                        
                        // Update DataStore with latest information
                        requireContext().dataStore.edit {
                            it[PrefKeys.USER_NAME] = profileData.data.user.full_name
                            it[PrefKeys.ACTIVE_ROLE_ID] = profileData.data.auth.roleId
                            it[PrefKeys.ACTIVE_ROLE_NAME] = profileData.data.user.activeContext.roleName
                        }
                    } else {
                        android.util.Log.e("ProfileFragment", "Profile API returned success=false")
                        android.util.Log.e("ProfileFragment", "Message: ${profileData?.message}")
                    }
                } else {
                    android.util.Log.e("ProfileFragment", "Profile API call failed")
                    android.util.Log.e("ProfileFragment", "Status code: ${response.code()}")
                    android.util.Log.e("ProfileFragment", "Message: ${response.message()}")
                    
                    when (response.code()) {
                        401 -> {
                            android.util.Log.e("ProfileFragment", "Unauthorized - token may be invalid")
                            // User needs to login again
                        }
                        403 -> {
                            android.util.Log.e("ProfileFragment", "Forbidden - insufficient permissions")
                        }
                        else -> {
                            android.util.Log.e("ProfileFragment", "Server error: ${response.code()}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "=== PROFILE ERROR ===")
                android.util.Log.e("ProfileFragment", "Error Type: ${e.javaClass.simpleName}")
                android.util.Log.e("ProfileFragment", "Error Message: ${e.message}")
                android.util.Log.e("ProfileFragment", "Error Details:", e)
            }
        }
    }
}


