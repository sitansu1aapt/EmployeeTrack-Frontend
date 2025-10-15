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

class TasksFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<ListView>(R.id.listTasks)
        val api = Network.retrofit.create<TaskApi>()
        lifecycleScope.launch {
            try {
                // TODO: load activeRoleId from DataStore if available
                val roleId = "1"
                val tasks = api.getAssignedTasks(roleId).data
                val items = tasks.map { "${'$'}{it.task_title} • ${'$'}{it.task_status}" }
                list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Failed to load tasks", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Minimal models mirroring RN
@Serializable
data class AssignedTask(
    val assignment_id: String,
    val task_id: String,
    val task_title: String,
    val task_description: String,
    val task_priority: String,
    val task_due_date: String,
    val task_status: String
)

@Serializable
data class AssignedTasksEnvelope(val status: String? = null, val data: List<AssignedTask> = emptyList())

interface TaskApi {
    @GET("task-assignments/assigned-to-me/organization")
    suspend fun getAssignedTasks(@Query("roleId") roleId: String): AssignedTasksEnvelope
}


