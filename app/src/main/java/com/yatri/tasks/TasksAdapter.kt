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
    var items: List<AssignedTask>,
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
            android.util.Log.d("TasksAdapter", "=== BINDING TASK ===")
            android.util.Log.d("TasksAdapter", "Task: ${task.taskTitle}, Status: ${task.taskStatus}")
            
            val tvTitle = itemView.findViewById<TextView>(R.id.tvTaskTitle)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvTaskDescription)
            val tvPriority = itemView.findViewById<TextView>(R.id.tvPriority)
            val tvDueDate = itemView.findViewById<TextView>(R.id.tvDueDate)
            val tvAssignedBy = itemView.findViewById<TextView>(R.id.tvAssignedBy)
            val tvAssignedByEmail = itemView.findViewById<TextView>(R.id.tvAssignedByEmail)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
            val btnAction = itemView.findViewById<Button>(R.id.btnAction)
            val attachmentSection = itemView.findViewById<View>(R.id.attachmentSection)
            val tvAttachmentName = itemView.findViewById<TextView>(R.id.tvAttachmentName)

            tvTitle.text = task.taskTitle
            tvDesc.text = task.taskDescription
            tvDueDate.text = "Due: ${task.taskDueDate}"
            tvAssignedBy.text = "Assigned by: ${task.assignedByName}"
            tvAssignedByEmail.text = task.assignedByEmail
            tvStatus.text = task.taskStatus

            // Priority badge color
            tvPriority.text = task.taskPriority
            when (task.taskPriority.uppercase()) {
                "HIGH" -> tvPriority.setBackgroundResource(R.drawable.bg_priority_high)
                "MEDIUM" -> tvPriority.setBackgroundResource(R.drawable.bg_priority_medium)
                "LOW" -> tvPriority.setBackgroundResource(R.drawable.bg_priority_low)
                else -> tvPriority.setBackgroundResource(R.drawable.bg_priority_high)
            }

            // File attachment section
            if (!task.attachmentUrl.isNullOrEmpty()) {
                attachmentSection.visibility = View.VISIBLE
                tvAttachmentName.setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.data = android.net.Uri.parse(task.attachmentUrl)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    try {
                        itemView.context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            itemView.context,
                            "Unable to open attachment.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                attachmentSection.visibility = View.GONE
            }

            // Set button visibility and text based on task status
            when (task.taskStatus) {
                "ASSIGNED" -> {
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "START TASK"
                    btnAction.setBackgroundResource(R.drawable.bg_button_blue)
                }
                "IN_PROGRESS" -> {
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "Request Completion"
                    btnAction.setBackgroundResource(R.drawable.bg_button_green)
                }
                "VERIFICATION_PENDING" -> {
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "Mark Completed"
                    btnAction.setBackgroundResource(R.drawable.bg_button_green)
                }
                else -> {
                    btnAction.visibility = View.GONE
                }
            }

            btnAction.setOnClickListener {
                android.util.Log.d("TasksAdapter", "Button clicked for task: ${task.taskTitle}, Status: ${task.taskStatus}")
                android.util.Log.d("TasksAdapter", "Calling onAction callback...")
                onAction(task)

                android.util.Log.d("TasksAdapter-onAction", "onAction called")

                android.util.Log.d("TasksAdapter", "onAction callback completed")

            }
        }
    }
}