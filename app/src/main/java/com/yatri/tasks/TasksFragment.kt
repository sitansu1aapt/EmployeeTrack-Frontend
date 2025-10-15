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
            // TODO: Call updateTaskStatusByAssignee based on status
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        tasksApi = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.example.com/") // TODO: Set real base URL
            .build()
            .create(TasksApi::class.java)
        loadTasks()
    }

    private fun loadTasks() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = tasksApi.getAssignedTasks(roleId)
                withContext(Dispatchers.Main) {
                    adapter.updateData(tasks)
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }
}
