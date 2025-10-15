package com.yatri

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Switch>(R.id.swBackground)?.setOnCheckedChangeListener { _, isChecked ->
            val ctx = requireContext()
            if (isChecked) ctx.startService(Intent(ctx, LocationService::class.java)) else ctx.stopService(Intent(ctx, LocationService::class.java))
        }
        view.findViewById<Button>(R.id.btnChangePassword)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Change Password: Coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        view.findViewById<android.widget.Spinner>(R.id.spinnerLanguage)?.let { spinner ->
            // TODO: Populate spinner with language options and handle selection
        }
        view.findViewById<Button>(R.id.btnUpdateFcm)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Update FCM Token: Coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnLogout)?.setOnClickListener {
            lifecycleScope.launch {
                requireContext().dataStore.edit { it.clear() }
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
        }
    }
}


