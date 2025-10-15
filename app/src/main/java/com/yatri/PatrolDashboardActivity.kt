package com.yatri

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yatri.patrol.PatrolApi
import com.yatri.patrol.PatrolSession
import com.yatri.patrol.StartPatrolBody
import com.yatri.net.Network
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.create

class PatrolDashboardActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private var sessions: List<PatrolSession> = emptyList()
    private val roleId: String = "1" // TODO: load from DataStore
    private val TAG = "PatrolDashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patrol_dashboard)
        list = findViewById(R.id.listPatrols)
        loadSessions()
        list.setOnItemClickListener { _, _, position, _ ->
            val s = sessions[position]
            if (s.status == "PLANNED") {
                AlertDialog.Builder(this)
                    .setTitle("Start Patrol")
                    .setMessage("Start patrol for ${s.route_name}?")
                    .setPositiveButton("Start") { _, _ -> startPatrol(s) }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else if (s.status == "IN_PROGRESS") {
                // Open ActivePatrol
                startActivity(Intent(this, ActivePatrolActivity::class.java).putExtra("sessionId", s.patrol_session_id))
            }
        }
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                val response: Response<com.yatri.patrol.Envelope<List<PatrolSession>>> = api.getEmpSessions(roleId)
                
                if (response.isSuccessful) {
                    val env = response.body()
                    sessions = env?.data ?: emptyList()
                    val items = sessions.map { "${it.route_name} • ${it.status}" }
                    list.adapter = ArrayAdapter(this@PatrolDashboardActivity, android.R.layout.simple_list_item_1, items)
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

    private fun startPatrol(s: PatrolSession) {
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                // RN sends routeId: parse from session if present, or fallback
                val routeId = s.patrol_session_id.filter { it.isDigit() }.toIntOrNull() ?: 0
                val response = api.startSession(roleId, StartPatrolBody(routeId))
                
                if (response.isSuccessful) {
                    val env = response.body()
                    Toast.makeText(this@PatrolDashboardActivity, env?.message ?: "Started", Toast.LENGTH_SHORT).show()
                    loadSessions()
                } else {
                    // Handle different error codes
                    when (response.code()) {
                        403 -> {
                            Log.e(TAG, "Forbidden: User doesn't have permission to start patrol")
                            Toast.makeText(this@PatrolDashboardActivity, "You don't have permission to start patrol sessions", Toast.LENGTH_LONG).show()
                        }
                        401 -> {
                            Log.e(TAG, "Unauthorized: Invalid or missing authentication token")
                            Toast.makeText(this@PatrolDashboardActivity, "Authentication required. Please log in again.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Log.e(TAG, "Failed to start patrol. Error code: ${response.code()}")
                            Toast.makeText(this@PatrolDashboardActivity, "Failed to start patrol. Error code: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while starting patrol", e)
                Toast.makeText(this@PatrolDashboardActivity, e.message ?: "Failed to start", Toast.LENGTH_LONG).show()
            }
        }
    }
}