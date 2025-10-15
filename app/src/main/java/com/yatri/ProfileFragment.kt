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

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                requireContext().dataStore.edit { it.clear() }
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
        }

        // Bind header placeholders (from DataStore if present)
        lifecycleScope.launch {
            val prefs = requireContext().dataStore.data.first()
            val name = prefs[PrefKeys.USER_NAME] ?: "test user"
            val role = prefs[PrefKeys.ACTIVE_ROLE_NAME] ?: "EMPLOYEE"
            view.findViewById<TextView>(R.id.tvName)?.text = name
            view.findViewById<TextView>(R.id.tvRole)?.text = role
        }
    }
}


