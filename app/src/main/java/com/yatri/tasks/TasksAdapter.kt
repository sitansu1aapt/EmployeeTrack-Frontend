package com.yatri.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksAdapter(
    private var items: List<AssignedTask>,
    private val onAction: (AssignedTask) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position], onAction)
    }
    override fun getItemCount() = items.size
    fun updateData(newItems: List<AssignedTask>) {
        items = newItems
        notifyDataSetChanged()
    }
    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(task: AssignedTask, onAction: (AssignedTask) -> Unit) {
            val tvTitle = itemView.findViewById<TextView>(R.id.tvTaskTitle)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvTaskDescription)
            val tvPriority = itemView.findViewById<TextView>(R.id.tvPriority)
            val tvDueDate = itemView.findViewById<TextView>(R.id.tvDueDate)
            val tvAssignedBy = itemView.findViewById<TextView>(R.id.tvAssignedBy)
            val tvAssignedByEmail = itemView.findViewById<TextView>(R.id.tvAssignedByEmail)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
            val btnAction = itemView.findViewById<Button>(R.id.btnAction)
            
            tvTitle.text = task.taskTitle
            tvDesc.text = task.taskDescription
            tvPriority.text = task.taskPriority
            tvDueDate.text = "Due: ${task.taskDueDate}"
            tvAssignedBy.text = "Assigned by: ${task.assignedByName}"
            tvAssignedByEmail.text = task.assignedByEmail
            tvStatus.text = task.taskStatus
            
            // Set button visibility and text based on task status
            when (task.taskStatus) {
                "ASSIGNED" -> {
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "START TASK"
                    btnAction.setBackgroundResource(R.drawable.bg_button_blue)
                    android.util.Log.d("TasksAdapter", "Set START TASK button visible for task: ${task.taskId}")
                }
                "IN_PROGRESS" -> {
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "Request Completion"
                    btnAction.setBackgroundResource(R.drawable.bg_button_green)
                    android.util.Log.d("TasksAdapter", "Set Request Completion button visible for task: ${task.taskId}")
                }
                else -> {
                    btnAction.visibility = View.GONE
                    android.util.Log.d("TasksAdapter", "Button hidden for task: ${task.taskId} with status: ${task.taskStatus}")
                }
            }
            
            // Set click listener for the action button
            btnAction.setOnClickListener {
                android.util.Log.d("TasksAdapter", "Button clicked for task: ${task.taskId}, status: ${task.taskStatus}")
                android.widget.Toast.makeText(
                    itemView.context,
                    "Processing task action: ${task.taskStatus}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onAction(task)
            }
        }
    }
}