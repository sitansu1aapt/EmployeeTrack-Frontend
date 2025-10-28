package com.yatri

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yatri.patrol.PatrolApi
import com.yatri.AppConfig
import com.yatri.patrol.PatrolSession
import com.yatri.patrol.StartPatrolBody
import com.yatri.net.Network
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.create

class PatrolDashboardActivity : AppCompatActivity() {
    private lateinit var containerPlanned: LinearLayout
    private lateinit var containerInProgress: LinearLayout
    private lateinit var containerHistory: LinearLayout
    private lateinit var emptyPlanned: TextView
    private lateinit var emptyInProgress: TextView
    private lateinit var emptyHistory: TextView
    private var sessions: List<PatrolSession> = emptyList()
    private var roleId: String? = null // Loaded from DataStore
    private val TAG = "PatrolDashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patrol_dashboard)
        containerPlanned = findViewById(R.id.containerPlanned)
        containerInProgress = findViewById(R.id.containerInProgress)
        containerHistory = findViewById(R.id.containerHistory)
        emptyPlanned = findViewById(R.id.emptyPlanned)
        emptyInProgress = findViewById(R.id.emptyInProgress)
        emptyHistory = findViewById(R.id.emptyHistory)

        // Load active roleId from DataStore (matches React Native)
        lifecycleScope.launch {
            try {
                val prefs = applicationContext.dataStore.data.first()
                roleId = prefs[PrefKeys.ACTIVE_ROLE_ID]
                Log.d(TAG, "Loaded roleId from DataStore: $roleId")
                loadSessions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load roleId from DataStore", e)
                loadSessions()
            }
        }
        // Item click is handled per card button now
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                val rid = roleId ?: ""
                Log.d(TAG, "Requesting sessions with roleId=$rid")
                val response: Response<com.yatri.patrol.Envelope<List<PatrolSession>>> = api.getEmpSessions(rid.ifEmpty { "" })
                
                if (response.isSuccessful) {
                    val env = response.body()
                    sessions = env?.data ?: emptyList()
                    renderSessions()
                } else {
                    // Handle different error codes
                    when (response.code()) {
                        403 -> {
                            Log.e(TAG, "Forbidden: User doesn't have permission to access patrol sessions")
                            Toast.makeText(this@PatrolDashboardActivity, "You don't have permission to access patrol sessions", Toast.LENGTH_LONG).show()
                        }
                        401 -> {
                            Log.e(TAG, "Unauthorized: Invalid or missing authentication token")
                            Toast.makeText(this@PatrolDashboardActivity, "Authentication required. Please log in again.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Log.e(TAG, "Failed to load patrol sessions. Error code: ${response.code()}")
                            Toast.makeText(this@PatrolDashboardActivity, "Failed to load patrol sessions. Error code: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading patrol sessions", e)
                Toast.makeText(this@PatrolDashboardActivity, e.message ?: "Failed to load", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderSessions() {
        containerPlanned.removeAllViews()
        containerInProgress.removeAllViews()
        containerHistory.removeAllViews()

        val planned = sessions.filter { it.status == "PLANNED" }
        val inProgress = sessions.filter { it.status == "IN_PROGRESS" }
        val others = sessions.filter { it.status != "PLANNED" && it.status != "IN_PROGRESS" }

        emptyPlanned.visibility = if (planned.isEmpty()) View.VISIBLE else View.GONE
        emptyInProgress.visibility = if (inProgress.isEmpty()) View.VISIBLE else View.GONE
        emptyHistory.visibility = if (others.isEmpty()) View.VISIBLE else View.GONE

        planned.forEach { addCard(containerPlanned, it) }
        inProgress.forEach { addCard(containerInProgress, it) }
        others.forEach { addCard(containerHistory, it) }
    }

    private fun addCard(parent: LinearLayout, s: PatrolSession) {
        val ctx = this
        val card = LayoutInflater.from(ctx).inflate(R.layout.item_patrol_card, parent, false)
        val tvRoute = card.findViewById<TextView>(R.id.tvRouteName)
        val tvBadge = card.findViewById<TextView>(R.id.tvStatusBadge)
        val tvSched = card.findViewById<TextView>(R.id.tvScheduled)
        val btnPrimary = card.findViewById<Button>(R.id.btnPrimary)
        val btnSecondary = card.findViewById<Button>(R.id.btnSecondary)

        tvRoute.text = s.route_name
        tvBadge.text = s.status
        when (s.status) {
            "PLANNED" -> {
                tvBadge.setBackgroundResource(R.drawable.badge_planned)
                tvSched.text = "Scheduled: ${s.scheduled_start_time ?: "--"}"
                btnPrimary.text = "Start Patrol"
                btnSecondary.visibility = View.GONE
                btnPrimary.setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("Start Patrol")
                        .setMessage("Start patrol for ${s.route_name}?")
                        .setPositiveButton("Start") { _, _ -> startPatrol(s) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            "IN_PROGRESS" -> {
                tvBadge.setBackgroundResource(R.drawable.badge_planned)
                tvSched.text = "In progress"
                btnPrimary.text = "Scan Checkpoint"
                btnPrimary.setOnClickListener {
                    // Navigate to active patrol scanning
                    lifecycleScope.launch {
                        val prefs = applicationContext.dataStore.data.first()
                        val roleId = prefs[PrefKeys.ACTIVE_ROLE_ID] ?: ""
                        Log.d(TAG, "Opening ActivePatrolActivity with sessionId=${s.patrol_session_id}, roleId=$roleId, routeName=${s.route_name}")
                        startActivity(Intent(ctx, ActivePatrolActivity::class.java)
                            .putExtra("sessionId", s.patrol_session_id)
                            .putExtra("routeName", s.route_name)
                            .putExtra("roleId", roleId))
                    }
                }
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Patrol Status"
                btnSecondary.setOnClickListener {
                    startActivity(Intent(ctx, PatrolStatusActivity::class.java).putExtra("sessionId", s.patrol_session_id))
                }
            }
            else -> {
                tvBadge.setBackgroundResource(R.drawable.badge_planned)
                tvSched.text = s.status
                btnPrimary.text = "Details"
                btnSecondary.visibility = View.GONE
                btnPrimary.setOnClickListener { }
            }
        }
        parent.addView(card)
    }

    private fun startPatrol(s: PatrolSession) {
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                
                // Extract routeId from patrol_route_id (matches React Native)
                val routeId = s.patrol_route_id?.toIntOrNull() ?: 0
                
                Log.d(TAG, "=== START PATROL API CALL ===")
                Log.d(TAG, "Role ID: $roleId")
                Log.d(TAG, "Route ID: $routeId")
                Log.d(TAG, "Session ID: ${s.patrol_session_id}")
                Log.d(TAG, "Route Name: ${s.route_name}")
                Log.d(TAG, "Request body: StartPatrolBody(routeId=$routeId)")
                val fullUrl = "${AppConfig.API_BASE_URL}employee/patrol/sessions/start?roleId=${roleId ?: ""}"
                Log.d(TAG, "URL: $fullUrl")
                
                val response = api.startSession(roleId ?: "", StartPatrolBody(routeId))
                
                Log.d(TAG, "=== START PATROL API RESPONSE ===")
                Log.d(TAG, "Status Code: ${response.code()}")
                Log.d(TAG, "Response Body: ${response.body()}")
                
                if (response.isSuccessful) {
                    val env = response.body()
                    Toast.makeText(this@PatrolDashboardActivity, env?.message ?: "Started", Toast.LENGTH_SHORT).show()
                    loadSessions()
                } else {
                    // Handle different error codes
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error Response Body: $errorBody")
                    
                    when (response.code()) {
                        403 -> {
                            Log.e(TAG, "Forbidden: User doesn't have permission to start patrol")
                            Toast.makeText(this@PatrolDashboardActivity, "You don't have permission to start patrol sessions", Toast.LENGTH_LONG).show()
                        }
                        401 -> {
                            Log.e(TAG, "Unauthorized: Invalid or missing authentication token")
                            Toast.makeText(this@PatrolDashboardActivity, "Authentication required. Please log in again.", Toast.LENGTH_LONG).show()
                        }
                        500 -> {
                            Log.e(TAG, "Internal Server Error: $errorBody")
                            val errorMessage = errorBody ?: "Internal server error"
                            Toast.makeText(this@PatrolDashboardActivity, "Server error: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Log.e(TAG, "Failed to start patrol. Error code: ${response.code()} body: $errorBody")
                            Toast.makeText(this@PatrolDashboardActivity, "Failed to start patrol. Error code: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== START PATROL EXCEPTION ===")
                Log.e(TAG, "Error Type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error Message: ${e.message}")
                Log.e(TAG, "Error Details:", e)
                Toast.makeText(this@PatrolDashboardActivity, e.message ?: "Failed to start", Toast.LENGTH_LONG).show()
            }
        }
    }
}