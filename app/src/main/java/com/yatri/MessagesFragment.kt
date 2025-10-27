package com.yatri

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yatri.notifications.Notification
import com.yatri.notifications.NotificationType
import com.yatri.notifications.NotificationApi
import com.yatri.net.Network
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yatri.PrefKeys
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

class MessagesFragment : Fragment() {
    private lateinit var adapter: NotificationAdapter
    private var allNotifications: List<Notification> = emptyList()
    private var filterType: String = ""
    private var filterSeen: String = "all"
    private var activeRoleId: String = ""
    // UI Components
    private lateinit var rvNotifications: RecyclerView
    private lateinit var tvSummary: TextView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvNotifications = view.findViewById(R.id.rvNotifications)
        tvSummary = view.findViewById(R.id.tvSummary)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        
        // Get active role ID from preferences
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = requireContext().dataStore.data.first()
            activeRoleId = prefs[PrefKeys.ACTIVE_ROLE_ID] ?: ""
        }
        
        setupRecyclerView()
        setupFilters(view)
        setupSwipeRefresh()
        loadNotifications()
    }
    private fun setupRecyclerView() {
        adapter = NotificationAdapter(emptyList(), onItemClick = { notification ->
            onNotificationClick(notification)
        })
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
    }
    private fun setupFilters(view: View) {
        val btnAllType = view.findViewById<Button>(R.id.btnAllType)
        val btnEmergency = view.findViewById<Button>(R.id.btnEmergency)
        val btnMessage = view.findViewById<Button>(R.id.btnMessage)
        val btnAllSeen = view.findViewById<Button>(R.id.btnAllSeen)
        val btnUnread = view.findViewById<Button>(R.id.btnUnread)
        val btnRead = view.findViewById<Button>(R.id.btnRead)

        val typeButtons = listOf(btnAllType, btnEmergency, btnMessage)
        val seenButtons = listOf(btnAllSeen, btnUnread, btnRead)

        fun highlightSelected(selected: Button, group: List<Button>) {
            group.forEach {
                it.isSelected = false
                it.setBackgroundResource(R.drawable.bg_tab_selector)
                it.setTextColor(resources.getColor(R.color.text_primary, null))
            }
            selected.isSelected = true
            selected.setBackgroundResource(R.drawable.bg_tab_selector)
            selected.setTextColor(resources.getColor(R.color.white, null))
        }

        // Default: only the first tab in each group is highlighted
        highlightSelected(btnAllType, typeButtons)
        highlightSelected(btnAllSeen, seenButtons)

        btnAllType.setOnClickListener {
            filterType = ""
            updateUI()
            highlightSelected(btnAllType, typeButtons)
        }
        btnEmergency.setOnClickListener {
            filterType = NotificationType.EMERGENCY_ALERT.value
            updateUI()
            highlightSelected(btnEmergency, typeButtons)
        }
        btnMessage.setOnClickListener {
            filterType = NotificationType.MESSAGE.value
            updateUI()
            highlightSelected(btnMessage, typeButtons)
        }
        btnAllSeen.setOnClickListener {
            filterSeen = "all"
            updateUI()
            highlightSelected(btnAllSeen, seenButtons)
        }
        btnUnread.setOnClickListener {
            filterSeen = "unread"
            updateUI()
            highlightSelected(btnUnread, seenButtons)
        }
        btnRead.setOnClickListener {
            filterSeen = "read"
            updateUI()
            highlightSelected(btnRead, seenButtons)
        }
    }
    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadNotifications(refresh = true)
        }
        swipeRefresh.setColorSchemeResources(R.color.blue_500)
    }
    private fun loadNotifications(refresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show loading state if needed
                val api = Network.retrofit.create(NotificationApi::class.java)
                val response = api.getNotifications(
                    activeRoleId,
                    if (filterType.isNotEmpty()) filterType else null,
                    when (filterSeen) {
                        "unread" -> false
                        "read" -> true
                        else -> null
                    },
                    20,
                    0
                )
                android.util.Log.d("MessagesFragment", "API response: ${response.raw()} body: ${response.body()}")
                if (response.isSuccessful) {
                    val notificationResponse = response.body()
                    val count = notificationResponse?.data?.notifications?.size ?: 0
                    android.util.Log.d("MessagesFragment", "Number of messages from API: $count")
                    if (notificationResponse?.status == "success") {
                        allNotifications = notificationResponse.data.notifications
                        updateUI()
                        val summary = notificationResponse.data.summary
                        tvSummary.text = "${summary.unreadCount} unread • ${summary.totalCount} total"
                    } else {
                        showError("Failed to load notifications")
                    }
                } else {
                    when (response.code()) {
                        401 -> showError("Authentication required. Please log in again.")
                        403 -> showError("You don't have permission to view notifications.")
                        else -> showError("Failed to load notifications. Error code: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MessagesFragment", "Error loading notifications", e)
                showError("Network error. Please check your connection.")
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }
    private fun updateUI() {
        val filtered = allNotifications.filter { notification ->
            val typeMatch = filterType.isEmpty() || notification.type == filterType
            val statusMatch = when (filterSeen) {
                "all" -> true
                "unread" -> !notification.is_seen
                "read" -> notification.is_seen
                else -> true
            }
            typeMatch && statusMatch
        }
        adapter.updateData(filtered)
        val unread = allNotifications.count { !it.is_seen }
        val total = allNotifications.size
        tvSummary.text = "$unread unread • $total total"
    }
    private fun onNotificationClick(notification: Notification) {
        if (!notification.is_seen) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val api = Network.retrofit.create(NotificationApi::class.java)
                    val response = api.markNotificationAsSeen(notification.notification_id)
                    if (response.isSuccessful) {
                        allNotifications = allNotifications.map { n ->
                            if (n.notification_id == notification.notification_id) {
                                n.copy(is_seen = true, seen_at = java.time.Instant.now().toString())
                            } else {
                                n
                            }
                        }
                        updateUI()
                    } else {
                        showError("Failed to mark notification as read")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MessagesFragment", "Error marking notification as seen", e)
                    showError("Network error occurred")
                }
            }
        }
        showNotificationPopup(notification)
    }

    private fun showNotificationPopup(notification: Notification) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle(notification.title)
            .setMessage(notification.details)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
    }
    private fun showError(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }
}



