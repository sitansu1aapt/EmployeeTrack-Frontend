package com.yatri

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.yatri.notifications.*
import com.yatri.net.Network
import com.yatri.localization.LocalizationManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import retrofit2.create
import java.text.SimpleDateFormat
import java.util.*

class NotificationActivity : AppCompatActivity() {
    
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvSummary: TextView
    private lateinit var rvNotifications: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: LinearLayout
    
    private lateinit var adapter: NotificationAdapter
    private var notifications = mutableListOf<Notification>()
    private var allNotifications = mutableListOf<Notification>()
    private var currentFilters = NotificationFilter()
    private var summary = NotificationSummary(0, 0)
    private var activeRoleId: String? = null
    
    private val TAG = "NotificationActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize localization
        LocalizationManager.initialize(this)
        
        setContentView(R.layout.activity_notification)
        
        initializeViews()
        setupRecyclerView()
        setupFilterButtons()
        loadActiveRoleId()
        fetchNotifications()
    }
    
    private fun initializeViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvSummary = findViewById(R.id.tvSummary)
        rvNotifications = findViewById(R.id.rvNotifications)
        swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        
        // Set localized header title
        tvHeaderTitle.text = getString(R.string.notification_message_management)
        
        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener {
            fetchNotifications(refresh = true)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = NotificationAdapter { notification ->
            onNotificationClick(notification)
        }
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = adapter
    }
    
    private fun setupFilterButtons() {
        // Type filters
        setupTypeFilterButtons()
        
        // Status filters
        setupStatusFilterButtons()
    }
    
    private fun setupTypeFilterButtons() {
        val typeContainer = findViewById<LinearLayout>(R.id.typeFilterContainer)
        typeContainer.removeAllViews()
        
        NotificationType.values().forEach { type ->
            val button = createFilterButton(type.displayName)
            button.setOnClickListener {
                selectTypeFilter(type, button)
            }
            typeContainer.addView(button)
        }
        
        // Select "All" by default
        if (typeContainer.childCount > 0) {
            (typeContainer.getChildAt(0) as Button).isSelected = true
        }
    }
    
    private fun setupStatusFilterButtons() {
        val statusContainer = findViewById<LinearLayout>(R.id.statusFilterContainer)
        statusContainer.removeAllViews()
        
        NotificationStatus.values().forEach { status ->
            val button = createFilterButton(status.displayName)
            button.setOnClickListener {
                selectStatusFilter(status, button)
            }
            statusContainer.addView(button)
        }
        
        // Select "All" by default
        if (statusContainer.childCount > 0) {
            (statusContainer.getChildAt(0) as Button).isSelected = true
        }
    }
    
    private fun createFilterButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(resources.getColor(android.R.color.white))
            textSize = 12f
            isAllCaps = false
            setPadding(16, 8, 16, 8)
            background = resources.getDrawable(R.drawable.filter_button_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
        }
    }
    
    private fun selectTypeFilter(type: NotificationType, button: Button) {
        // Update button states
        val typeContainer = findViewById<LinearLayout>(R.id.typeFilterContainer)
        for (i in 0 until typeContainer.childCount) {
            (typeContainer.getChildAt(i) as Button).isSelected = false
        }
        button.isSelected = true
        
        // Update filter
        currentFilters = currentFilters.copy(type = type.value.ifEmpty { null })
        applyFilters()
    }
    
    private fun selectStatusFilter(status: NotificationStatus, button: Button) {
        // Update button states
        val statusContainer = findViewById<LinearLayout>(R.id.statusFilterContainer)
        for (i in 0 until statusContainer.childCount) {
            (statusContainer.getChildAt(i) as Button).isSelected = false
        }
        button.isSelected = true
        
        // Update filter
        currentFilters = currentFilters.copy(is_seen = status.value)
        applyFilters()
    }
    
    private fun loadActiveRoleId() {
        lifecycleScope.launch {
            try {
                val prefs = applicationContext.dataStore.data.first()
                activeRoleId = prefs[PrefKeys.ACTIVE_ROLE_ID]
                Log.d(TAG, "Loaded activeRoleId: $activeRoleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load activeRoleId", e)
            }
        }
    }
    
    private fun fetchNotifications(refresh: Boolean = false) {
        if (!refresh) {
            progressBar.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
        
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<NotificationApi>()
                val response = api.getNotifications(
                    userRole = activeRoleId,
                    type = currentFilters.type,
                    isSeen = currentFilters.is_seen,
                    limit = currentFilters.limit,
                    offset = currentFilters.offset
                )
                
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        allNotifications.clear()
                        allNotifications.addAll(data.notifications)
                        summary = data.summary
                        updateUI()
                        applyFilters()
                    }
                } else {
                    Log.e(TAG, "Failed to fetch notifications: ${response.code()}")
                    showError("Failed to fetch notifications")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching notifications", e)
                showError("Error: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun applyFilters() {
        notifications.clear()
        
        allNotifications.forEach { notification ->
            var matches = true
            
            // Type filter
            if (!currentFilters.type.isNullOrEmpty()) {
                matches = matches && notification.type == currentFilters.type
            }
            
            // Status filter
            if (currentFilters.is_seen != null) {
                matches = matches && notification.is_seen == currentFilters.is_seen
            }
            
            if (matches) {
                notifications.add(notification)
            }
        }
        
        adapter.updateNotifications(notifications)
        updateEmptyState()
    }
    
    private fun updateUI() {
        // Update summary
        tvSummary.text = getString(R.string.notification_summary, summary.unreadCount, summary.totalCount)
    }
    
    private fun updateEmptyState() {
        if (notifications.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            rvNotifications.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            rvNotifications.visibility = View.VISIBLE
        }
    }
    
    private fun onNotificationClick(notification: Notification) {
        // Mark as seen if not already seen
        if (!notification.is_seen) {
            markNotificationAsSeen(notification.notification_id)
        }
        
        // Show notification details (could open a detail screen or modal)
        showNotificationDetails(notification)
    }
    
    private fun markNotificationAsSeen(notificationId: String) {
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<NotificationApi>()
                api.markNotificationAsSeen(notificationId)
                
                // Update local data
                allNotifications.find { it.notification_id == notificationId }?.let { notification ->
                    val index = allNotifications.indexOf(notification)
                    allNotifications[index] = notification.copy(is_seen = true)
                }
                
                // Refresh summary
                summary = summary.copy(unreadCount = maxOf(0, summary.unreadCount - 1))
                updateUI()
                applyFilters()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as seen", e)
            }
        }
    }
    
    private fun showNotificationDetails(notification: Notification) {
        // Create and show a detail dialog or navigate to detail screen
        val intent = Intent(this, NotificationDetailActivity::class.java)
        intent.putExtra("notification", notification)
        startActivity(intent)
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    
    private val notifications = mutableListOf<Notification>()
    
    fun updateNotifications(newNotifications: List<Notification>) {
        notifications.clear()
        notifications.addAll(newNotifications)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }
    
    override fun getItemCount(): Int = notifications.size
    
    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val ivUnreadIndicator: View = itemView.findViewById(R.id.ivUnreadIndicator)
        
        fun bind(notification: Notification) {
            // Set icon based on notification type
            setNotificationIcon(notification.type)
            
            // Set title and color based on type
            tvTitle.text = getNotificationTitle(notification.type, notification)
            setTitleColor(notification.type)
            
            // Set details
            tvDetails.text = getNotificationDetails(notification)
            
            // Set timestamp
            tvTimestamp.text = formatTimestamp(notification.created_at)
            
            // Set content
            tvContent.text = getNotificationContent(notification)
            
            // Set unread indicator
            ivUnreadIndicator.visibility = if (notification.is_seen) View.GONE else View.VISIBLE
            
            // Set click listener
            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }
        
        private fun setNotificationIcon(type: String) {
            val iconRes = when (type) {
                "GEOFENCE_IN" -> R.drawable.ic_geofence_entry
                "GEOFENCE_OUT" -> R.drawable.ic_geofence_exit
                "ABSENT_NOTIFICATION" -> R.drawable.ic_absent_notification
                "EMERGENCY_NOTIFICATION" -> R.drawable.ic_emergency_alert
                "MESSAGE_NOTIFICATION" -> R.drawable.ic_message_notification
                else -> R.drawable.ic_notification_default
            }
            ivIcon.setImageResource(iconRes)
        }
        
        private fun getNotificationTitle(type: String, notification: Notification): String {
            return when (type) {
                "GEOFENCE_IN" -> itemView.context.getString(R.string.geofence_entry)
                "GEOFENCE_OUT" -> itemView.context.getString(R.string.geofence_exit)
                "ABSENT_NOTIFICATION" -> itemView.context.getString(R.string.attendance_absent_notification)
                "EMERGENCY_NOTIFICATION" -> itemView.context.getString(R.string.emergency_alert)
                "MESSAGE_NOTIFICATION" -> itemView.context.getString(R.string.message_notification)
                else -> notification.title
            }
        }
        
        private fun setTitleColor(type: String) {
            val colorRes = when (type) {
                "GEOFENCE_IN" -> R.color.success
                "GEOFENCE_OUT" -> R.color.warning
                "ABSENT_NOTIFICATION" -> R.color.text_secondary
                "EMERGENCY_NOTIFICATION" -> R.color.error
                else -> R.color.text_primary
            }
            tvTitle.setTextColor(itemView.context.resources.getColor(colorRes))
        }
        
        private fun getNotificationDetails(notification: Notification): String {
            return when (notification.type) {
                "GEOFENCE_IN" -> itemView.context.getString(R.string.geofence_entry_details, notification.metadata?.site_name ?: "")
                "GEOFENCE_OUT" -> itemView.context.getString(R.string.geofence_exit_details, notification.metadata?.site_name ?: "")
                else -> notification.details
            }
        }
        
        private fun getNotificationContent(notification: Notification): String {
            return when (notification.type) {
                "GEOFENCE_IN" -> itemView.context.getString(
                    R.string.geofence_entry_message,
                    notification.metadata?.user_name ?: "",
                    notification.metadata?.empid ?: "",
                    notification.metadata?.site_name ?: ""
                )
                "GEOFENCE_OUT" -> itemView.context.getString(
                    R.string.geofence_exit_message,
                    notification.metadata?.user_name ?: "",
                    notification.metadata?.empid ?: "",
                    notification.metadata?.site_name ?: ""
                )
                "ABSENT_NOTIFICATION" -> itemView.context.getString(
                    R.string.absent_notification_message,
                    notification.metadata?.user_name ?: "",
                    notification.metadata?.empid ?: "",
                    notification.created_at.substring(0, 10) // Extract date
                )
                else -> notification.details
            }
        }
        
        private fun formatTimestamp(timestamp: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(timestamp)
                outputFormat.format(date)
            } catch (e: Exception) {
                timestamp
            }
        }
    }
}
