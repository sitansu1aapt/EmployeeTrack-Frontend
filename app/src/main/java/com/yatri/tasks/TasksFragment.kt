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

class TasksFragment : Fragment() {
    private lateinit var adapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tasksApi: TasksApi
    private lateinit var spinnerFilter: android.widget.Spinner
    private lateinit var spinnerSort: android.widget.Spinner
    private var allTasks: List<AssignedTask> = emptyList()
    private var roleId: String = "EMP800" // TODO: Get from logged-in user
    private val gson = Gson()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        spinnerFilter = view.findViewById(R.id.spinnerFilter)
        spinnerSort = view.findViewById(R.id.spinnerSort)

        adapter = TasksAdapter(emptyList()) { task ->
            android.util.Log.d("TasksFragment", "=== BUTTON CLICKED ===")
            android.util.Log.d("TasksFragment", "Task clicked: ${task.taskTitle}, Status: ${task.taskStatus}")
            handleStatusUpdate(task)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Setup filter spinner
        val filterOptions = arrayOf("All", "ASSIGNED", "IN_PROGRESS", "VERIFICATION_PENDING", "COMPLETED", "HIGH", "MEDIUM", "LOW")
        spinnerFilter.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filterOptions)
        spinnerFilter.setSelection(0)
        spinnerFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                updateFilteredSortedTasks()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Setup sort spinner
        val sortOptions = arrayOf("Due Date", "Priority")
        spinnerSort.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortOptions)
        spinnerSort.setSelection(0)
        spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                updateFilteredSortedTasks()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        // Initialize Retrofit without logging interceptor for release builds
        // val loggingInterceptor = HttpLoggingInterceptor().apply {
        //     level = HttpLoggingInterceptor.Level.BODY
        // }
        
        val client = OkHttpClient.Builder()
            // .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val req = chain.request()
                val tok = com.yatri.TokenStore.token
                android.util.Log.d("TasksFragment", "API Request: ${req.method} ${req.url}")
                android.util.Log.d("TasksFragment", "Using token: ${if (tok.isNullOrBlank()) "NO TOKEN" else "Token exists"}")
                
                // Add authentication header if token exists
                val authReq = if (!tok.isNullOrBlank()) {
                    req.newBuilder().addHeader("Authorization", "Bearer $tok").build()
                } else {
                    req
                }
                
                val response = chain.proceed(authReq)
                android.util.Log.d("TasksFragment", "API Response: ${response.code}")
                response
            }
            .build()
            
        tasksApi = Retrofit.Builder()
            .baseUrl(com.yatri.AppConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(TasksApi::class.java)
        android.util.Log.d("TasksFragment", "Retrofit initialized with logging interceptor")
        loadTasks()
    }
    
    private fun handleStatusUpdate(task: AssignedTask) {
        android.util.Log.d("TasksFragment", "=== API CALL DEBUGGING ===")
        android.util.Log.d("TasksFragment", "handleStatusUpdate called for task: ${task.taskId}, current status: ${task.taskStatus}")
        android.util.Log.d("TasksFragment", "Task details: assignmentId=${task.assignmentId}, title=${task.taskTitle}")
        
        val newStatus = when (task.taskStatus) {
            "ASSIGNED" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "VERIFICATION_PENDING"
            "VERIFICATION_PENDING" -> "COMPLETED"
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
            "COMPLETED" -> "Task marked as completed!"
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
                    val result = tasksApi.updateTaskStatusByAssignee(
                        roleId,
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
                        "COMPLETED" -> "Task marked as completed!"
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
                val envelope = tasksApi.getAssignedTasks(roleId)
                withContext(Dispatchers.Main) {
                    allTasks = envelope.data
                    updateFilteredSortedTasks()
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = if (envelope.data.isEmpty()) View.VISIBLE else View.GONE
                    val noTasksStub = view?.findViewById<ViewGroup?>(R.id.noTasksStub)
                    if (envelope.data.isEmpty()) {
                        val inflater = LayoutInflater.from(requireContext())
                        val noTasksView = inflater.inflate(R.layout.view_no_tasks, noTasksStub, false)
                        noTasksStub?.removeAllViews()
                        noTasksStub?.addView(noTasksView)
                    } else {
                        noTasksStub?.removeAllViews()
                    }
                }
            } catch (e: Exception) {
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

    private fun updateFilteredSortedTasks() {
        var filtered = allTasks
        val filterValue = spinnerFilter.selectedItem?.toString() ?: "All"
        if (filterValue != "All") {
            filtered = filtered.filter {
                it.taskStatus == filterValue || it.taskPriority.equals(filterValue, ignoreCase = true)
            }
        }
        val sortValue = spinnerSort.selectedItem?.toString() ?: "Due Date"
        filtered = when (sortValue) {
            "Due Date" -> filtered.sortedBy { it.taskDueDate }
            "Priority" -> filtered.sortedBy { priorityOrder(it.taskPriority) }
            else -> filtered
        }
        adapter.updateData(filtered)
    }

    private fun priorityOrder(priority: String): Int {
        return when (priority.uppercase()) {
            "HIGH" -> 1
            "MEDIUM" -> 2
            "LOW" -> 3
            else -> 4
        }
    }
}
