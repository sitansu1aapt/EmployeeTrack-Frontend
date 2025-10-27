package com.yatri.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import okhttp3.OkHttpClient
// import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.IOException
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import android.content.Context
import com.yatri.dataStore

class TasksFragment : Fragment() {
    private lateinit var adapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private val tasksApi: TasksApi by lazy { com.yatri.net.Network.retrofit.create(TasksApi::class.java) }
    private var roleId: String? = null // Loaded from DataStore
    private val gson = Gson()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.d("TasksFragment", "Token before loading tasks: ${com.yatri.TokenStore.token}")
        val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        // Load active roleId from DataStore
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = requireContext().dataStore.data.first()
            roleId = prefs[com.yatri.PrefKeys.ACTIVE_ROLE_ID]
            android.util.Log.d("TasksFragment", "Loaded roleId from DataStore: $roleId")
            // Now you can safely load tasks
            setupAdapterAndLoadTasks(rv)
        }
    }

    private fun setupAdapterAndLoadTasks(rv: RecyclerView) {
        // Create adapter with explicit debug logging for the button click handler
        adapter = TasksAdapter(emptyList()) { task ->
            android.util.Log.d("TasksFragment", "onAction lambda called for task: ${task.taskId}, status: ${task.taskStatus}, adapter instance: ${adapter.hashCode()}")
            handleStatusUpdate(task)
        }
        android.util.Log.d("TasksFragment", "Adapter instance after creation: ${adapter.hashCode()}")
        android.util.Log.d("TasksFragment", "RecyclerView.adapter instance: ${rv.adapter?.hashCode()} (should match fragment adapter: ${adapter.hashCode()})")

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        // Use Network.retrofit for all API calls to ensure Authorization header is set
        android.util.Log.d("TasksFragment", "Retrofit initialized with Network.retrofit")
        loadTasks()
    }
    
    private fun handleStatusUpdate(task: AssignedTask) {
        android.util.Log.d("TasksFragment", "handleStatusUpdate ENTERED for task: ${task.taskId}, status: ${task.taskStatus}")
        android.util.Log.d("TasksFragment", "=== API CALL DEBUGGING ===")
        android.util.Log.d("TasksFragment", "handleStatusUpdate called for task: ${task.taskId}, current status: ${task.taskStatus}")
        android.util.Log.d("TasksFragment", "Task details: assignmentId=${task.assignmentId}, title=${task.taskTitle}")
        
        val newStatus = when (task.taskStatus) {
            "ASSIGNED" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "VERIFICATION_PENDING"
            else -> {
                android.util.Log.e("TasksFragment", "Invalid task status for update: ${task.taskStatus}")
                return
            }
        }

        android.util.Log.d("TasksFragment", "Will update status to: $newStatus")

        // Show immediate feedback to user
        val feedbackMessage = when (newStatus) {
            "IN_PROGRESS" -> "Starting task..."
            "VERIFICATION_PENDING" -> "Requesting completion..."
            else -> "Updating task status..."
        }

        android.widget.Toast.makeText(
            context,
            feedbackMessage,
            android.widget.Toast.LENGTH_SHORT
        ).show()

        android.util.Log.d("TasksFragment", "Updating task status to: $newStatus")
        progressBar.visibility = View.VISIBLE

        // Create a copy of the task with updated status for immediate UI update
        val updatedTask = task.copy(taskStatus = newStatus)
        val currentTasks = (adapter.items as? List<AssignedTask>) ?: emptyList()
        val updatedTasks = currentTasks.map {
            if (it.assignmentId == task.assignmentId) updatedTask else it
        }

        // Update UI immediately
        adapter.updateData(updatedTasks)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBody = mapOf("task_status" to newStatus)
                android.util.Log.d("TasksFragment", "Making API call with roleId: $roleId, assignmentId: ${task.assignmentId}")
                android.util.Log.d("TasksFragment", "Request body: ${gson.toJson(requestBody)}")

                // Log the full URL that will be called
                val baseUrl = com.yatri.AppConfig.API_BASE_URL
                val endpoint = "task-assignments/${task.assignmentId}/status/assignee?roleId=$roleId"
                android.util.Log.d("TasksFragment", "Full API URL: $baseUrl$endpoint")

                // Force a delay to ensure logs are visible
                android.util.Log.d("TasksFragment", "About to execute API call...")

                // Execute the API call with explicit try/catch
                try {
                    val rid = roleId ?: ""
                    val result = tasksApi.updateTaskStatusByAssignee(
                        rid,
                        task.assignmentId,
                        requestBody
                    )
                    android.util.Log.d("TasksFragment", "API call executed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("TasksFragment", "Inner exception during API call: ${e.javaClass.simpleName}: ${e.message}")
                    throw e
                }
                android.util.Log.d("TasksFragment", "API call successful, reloading tasks")

                withContext(Dispatchers.Main) {
                    val successMessage = when (newStatus) {
                        "IN_PROGRESS" -> "Task started successfully"
                        "VERIFICATION_PENDING" -> "Completion request sent"
                        else -> "Task updated successfully!"
                    }

                    android.widget.Toast.makeText(
                        context,
                        successMessage,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

                // Reload tasks to get the updated list from the server
                loadTasks()
            } catch (e: Exception) {
                // Enhanced error logging
                android.util.Log.e("TasksFragment", "Error updating task status: ${e.message}", e)
                android.util.Log.e("TasksFragment", "Error type: ${e.javaClass.simpleName}")

                if (e is retrofit2.HttpException) {
                    try {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("TasksFragment", "HTTP Error code: ${e.code()}")
                        android.util.Log.e("TasksFragment", "Error response body: $errorBody")
                    } catch (ex: Exception) {
                        android.util.Log.e("TasksFragment", "Failed to parse error body: ${ex.message}")
                    }
                } else if (e is java.io.IOException) {
                    android.util.Log.e("TasksFragment", "Network error (possible timeout or no connection)")
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    // Show more descriptive error message to user
                    val errorMessage = when (e) {
                        is retrofit2.HttpException -> "Server error (${e.code()}). Please try again."
                        is java.io.IOException -> "Network error. Please check your connection."
                        else -> "Failed to update task status: ${e.message}"
                    }

                    android.widget.Toast.makeText(
                        context,
                        errorMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    // Reload original tasks
                    loadTasks()
                }
            }
        }
    }

    private fun loadTasks() {
        android.util.Log.d("TasksFragment", "loadTasks called, fetching tasks for roleId: $roleId")
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        val noTasksStub = view?.findViewById<ViewGroup?>(R.id.noTasksStub)
        noTasksStub?.removeAllViews()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TasksFragment", "Making API call to get assigned tasks")
                val envelope = tasksApi.getAssignedTasks(roleId ?: "")
                android.util.Log.d("TasksFragment", "Received ${envelope.data.size} tasks from API")
                
                withContext(Dispatchers.Main) {
                    adapter.updateData(envelope.data)
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = if (envelope.data.isEmpty()) View.VISIBLE else View.GONE
                    val noTasksStub = view?.findViewById<ViewGroup?>(R.id.noTasksStub)
                    if (envelope.data.isEmpty()) {
                        // Inflate and show the animated no-tasks view
                        val inflater = LayoutInflater.from(requireContext())
                        val noTasksView = inflater.inflate(R.layout.view_no_tasks, noTasksStub, false)
                        noTasksStub?.removeAllViews()
                        noTasksStub?.addView(noTasksView)
                    } else {
                        noTasksStub?.removeAllViews()
                    }
                    
                    // Log task statuses for debugging
                    envelope.data.forEach { task ->
                        android.util.Log.d("TasksFragment", "Task ${task.taskId}: status=${task.taskStatus}, title=${task.taskTitle}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TasksFragment", "Error loading tasks: ${e.message}", e)
                android.util.Log.e("TasksFragment", "Error type: ${e.javaClass.simpleName}")
                
                if (e is retrofit2.HttpException) {
                    try {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("TasksFragment", "HTTP Error code: ${e.code()}")
                        android.util.Log.e("TasksFragment", "Error response body: $errorBody")
                    } catch (ex: Exception) {
                        android.util.Log.e("TasksFragment", "Failed to parse error body: ${ex.message}")
                    }
                } else if (e is java.io.IOException) {
                    android.util.Log.e("TasksFragment", "Network error (possible timeout or no connection)")
                }
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    
                    val errorMessage = when (e) {
                        is retrofit2.HttpException -> "Server error (${e.code()}). Please try again."
                        is java.io.IOException -> "Network error. Please check your connection."
                        else -> "Failed to load tasks: ${e.message}"
                    }
                    
                    android.widget.Toast.makeText(
                        context,
                        errorMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
