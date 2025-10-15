package com.yatri.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksFragment : Fragment() {
    private lateinit var adapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tasksApi: TasksApi
    private var roleId: String = "EMP800" // TODO: Get from logged-in user

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        adapter = TasksAdapter(emptyList()) { task ->
            handleStatusUpdate(task)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        // Create Retrofit with proper JSON converter
        tasksApi = retrofit2.Retrofit.Builder()
            .baseUrl(com.yatri.AppConfig.API_BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .client(okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    android.util.Log.d("TasksFragment", "API Request: ${request.method} ${request.url}")
                    val response = chain.proceed(request)
                    android.util.Log.d("TasksFragment", "API Response: ${response.code}")
                    response
                }
                .build())
            .build()
            .create(TasksApi::class.java)
        loadTasks()
    }
    
    private fun handleStatusUpdate(task: AssignedTask) {
        android.util.Log.d("TasksFragment", "handleStatusUpdate called for task: ${task.taskId}, current status: ${task.taskStatus}")
        
        val newStatus = when (task.taskStatus) {
            "ASSIGNED" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "VERIFICATION_PENDING"
            else -> {
                android.util.Log.e("TasksFragment", "Invalid task status for update: ${task.taskStatus}")
                return
            }
        }
        
        android.util.Log.d("TasksFragment", "Updating task status to: $newStatus")
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TasksFragment", "Making API call with roleId: $roleId, assignmentId: ${task.assignmentId}")
                tasksApi.updateTaskStatusByAssignee(
                    roleId,
                    task.assignmentId,
                    mapOf("status" to newStatus)
                )
                android.util.Log.d("TasksFragment", "API call successful, reloading tasks")
                // Reload tasks after successful update
                loadTasks()
            } catch (e: Exception) {
                android.util.Log.e("TasksFragment", "Error updating task status", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // Show error message to user
                    android.widget.Toast.makeText(
                        context,
                        "Failed to update task: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadTasks() {
        android.util.Log.d("TasksFragment", "loadTasks called, fetching tasks for roleId: $roleId")
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TasksFragment", "Making API call to get assigned tasks")
                val envelope = tasksApi.getAssignedTasks(roleId)
                android.util.Log.d("TasksFragment", "Received ${envelope.data.size} tasks from API")
                
                withContext(Dispatchers.Main) {
                    adapter.updateData(envelope.data)
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = if (envelope.data.isEmpty()) View.VISIBLE else View.GONE
                    
                    // Log task statuses for debugging
                    envelope.data.forEach { task ->
                        android.util.Log.d("TasksFragment", "Task ${task.taskId}: status=${task.taskStatus}, title=${task.taskTitle}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TasksFragment", "Error loading tasks", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    android.widget.Toast.makeText(
                        context,
                        "Failed to load tasks: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
