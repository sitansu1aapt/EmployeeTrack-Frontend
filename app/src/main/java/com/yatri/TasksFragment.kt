// This file is intentionally left empty to avoid duplicate TasksFragment definition.
// Use com.yatri.tasks.TasksFragment instead for all references.

package com.yatri

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yatri.net.Network
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.create
import androidx.recyclerview.widget.RecyclerView
import com.yatri.tasks.TasksAdapter
import androidx.recyclerview.widget.LinearLayoutManager

class TasksFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
        val api = Network.retrofit.create(com.yatri.tasks.TasksApi::class.java)
        val adapter = TasksAdapter(emptyList()) { /* TODO: action */ }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        lifecycleScope.launch {
            try {
                // TODO: load activeRoleId from DataStore if available
                val roleId = "1"
                val envelope = api.getAssignedTasks(roleId)
                adapter.updateData(envelope.data)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Failed to load tasks", Toast.LENGTH_LONG).show()
            }
        }
    }
}



