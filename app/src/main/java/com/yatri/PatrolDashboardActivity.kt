package com.yatri

import android.content.Intent
import android.os.Bundle
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
import retrofit2.create

class PatrolDashboardActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private var sessions: List<PatrolSession> = emptyList()
    private val roleId: String = "1" // TODO: load from DataStore

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
                val env = api.getEmpSessions(roleId)
                sessions = env.data ?: emptyList()
                val items = sessions.map { "${'$'}{it.route_name} • ${'$'}{it.status}" }
                list.adapter = ArrayAdapter(this@PatrolDashboardActivity, android.R.layout.simple_list_item_1, items)
            } catch (e: Exception) {
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
                val env = api.startSession(roleId, StartPatrolBody(routeId))
                Toast.makeText(this@PatrolDashboardActivity, env.message ?: "Started", Toast.LENGTH_SHORT).show()
                loadSessions()
            } catch (e: Exception) {
                Toast.makeText(this@PatrolDashboardActivity, e.message ?: "Failed to start", Toast.LENGTH_LONG).show()
            }
        }
    }
}



