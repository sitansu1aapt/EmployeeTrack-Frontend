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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesFragment : Fragment() {
    // private lateinit var adapter: NotificationAdapter // TODO: Implement adapter
    private var allNotifications: List<Notification> = emptyList()
    private var filterType: String = ""
    private var filterSeen: String = "all"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvNotifications)
        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        // adapter = NotificationAdapter(emptyList())
        rv.layoutManager = LinearLayoutManager(requireContext())
        // rv.adapter = adapter

        fun updateUI() {
            val filtered = allNotifications.filter {
                (filterType.isEmpty() || it.type == filterType) &&
                (filterSeen == "all" || (filterSeen == "unread" && !it.is_seen) || (filterSeen == "read" && it.is_seen))
            }
            // adapter.updateData(filtered)
            val unread = allNotifications.count { !it.is_seen }
            val total = allNotifications.size
            tvSummary.text = "$unread unread • $total total"
        }

        view.findViewById<Button>(R.id.btnAllType).setOnClickListener {
            filterType = ""
            updateUI()
        }
        view.findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            filterType = "EMERGENCY_NOTIFICATION"
            updateUI()
        }
        view.findViewById<Button>(R.id.btnMessage).setOnClickListener {
            filterType = "MESSAGE_NOTIFICATION"
            updateUI()
        }
        view.findViewById<Button>(R.id.btnAllSeen).setOnClickListener {
            filterSeen = "all"
            updateUI()
        }
        view.findViewById<Button>(R.id.btnUnread).setOnClickListener {
            filterSeen = "unread"
            updateUI()
        }
        view.findViewById<Button>(R.id.btnRead).setOnClickListener {
            filterSeen = "read"
            updateUI()
        }

        // CoroutineScope(Dispatchers.IO).launch {
        //     // TODO: Replace with real API call
        //     val notifications = listOf(
        //         NotificationItem("1", "Geofence Entry: O-HUB", "Oct 15, 2025 19:11", "FAGURAM MURMU (EmpID: YCPSL-1695) entered site: O-HUB", "GEOFENCE_IN", false),
        //         NotificationItem("2", "Geofence Exit: O-HUB", "Oct 15, 2025 18:22", "FAGURAM MURMU (EmpID: YCPSL-1695) exited site: O-HUB", "GEOFENCE_OUT", true),
        //         NotificationItem("3", "Absent Notification", "Oct 15, 2025 16:00", "Employee CHIRANJEEV GARU (EmpID: EMP1119) was marked ABSENT on 2025-10-15", "ATTENDANCE", false)
        //     )
        //     withContext(Dispatchers.Main) {
        //         allNotifications = notifications
        //         updateUI()
        //     }
        // }
    }
}



