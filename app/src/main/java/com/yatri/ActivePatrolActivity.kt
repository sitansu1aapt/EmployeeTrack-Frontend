package com.yatri

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yatri.patrol.PatrolApi
import com.yatri.patrol.EndPatrolBody
import com.yatri.net.Network
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.create

class ActivePatrolActivity : AppCompatActivity() {
    private lateinit var tvRouteName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnEndPatrol: Button
    private var sessionId: String? = null
    private val roleId: String = "1" // TODO: load from DataStore
    private val TAG = "ActivePatrol"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_patrol)
        
        sessionId = intent.getStringExtra("sessionId")
        
        tvRouteName = findViewById(R.id.tvRouteName)
        tvStatus = findViewById(R.id.tvStatus)
        btnEndPatrol = findViewById(R.id.btnEndPatrol)
        
        btnEndPatrol.setOnClickListener {
            showEndPatrolDialog()
        }
        
        loadPatrolSession()
    }
    
    private fun loadPatrolSession() {
        // TODO: Implement loading patrol session details
        // This would involve calling an API to get the current patrol session details
        tvRouteName.text = "Loading patrol data..."
        tvStatus.text = "Please wait"
    }
    
    private fun showEndPatrolDialog() {
        AlertDialog.Builder(this)
            .setTitle("End Patrol")
            .setMessage("Are you sure you want to end this patrol session?")
            .setPositiveButton("End Patrol") { _, _ -> endPatrolSession() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun endPatrolSession() {
        val sessionId = this.sessionId ?: return
        
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create<PatrolApi>()
                val response: Response<com.yatri.patrol.Envelope<Unit>> = api.endSession(
                    sessionId, 
                    roleId, 
                    EndPatrolBody("Patrol completed successfully.")
                )
                
                if (response.isSuccessful) {
                    val env = response.body()
                    Toast.makeText(this@ActivePatrolActivity, env?.message ?: "Patrol ended successfully", Toast.LENGTH_SHORT).show()
                    finish() // Close this activity and return to the dashboard
                } else {
                    // Handle different error codes
                    when (response.code()) {
                        403 -> {
                            Log.e(TAG, "Forbidden: User doesn't have permission to end patrol")
                            Toast.makeText(this@ActivePatrolActivity, "You don't have permission to end patrol sessions", Toast.LENGTH_LONG).show()
                        }
                        401 -> {
                            Log.e(TAG, "Unauthorized: Invalid or missing authentication token")
                            Toast.makeText(this@ActivePatrolActivity, "Authentication required. Please log in again.", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Log.e(TAG, "Failed to end patrol. Error code: ${response.code()}")
                            Toast.makeText(this@ActivePatrolActivity, "Failed to end patrol. Error code: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while ending patrol", e)
                Toast.makeText(this@ActivePatrolActivity, e.message ?: "Failed to end patrol", Toast.LENGTH_LONG).show()
            }
        }
    }
}