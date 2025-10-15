package com.yatri.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yatri.R

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
            tvDueDate.text = "Due: ${task.dueDate}"
            tvAssignedBy.text = "Assigned by: ${task.assignedBy}"
            tvAssignedByEmail.text = task.assignedByEmail
            tvStatus.text = task.taskStatus
            btnAction.visibility = if (task.taskStatus == "ASSIGNED" || task.taskStatus == "IN_PROGRESS") View.VISIBLE else View.GONE
            btnAction.text = if (task.taskStatus == "ASSIGNED") "Start Task" else "Request Completion"
            btnAction.setOnClickListener { onAction(task) }
        }
    }
}
