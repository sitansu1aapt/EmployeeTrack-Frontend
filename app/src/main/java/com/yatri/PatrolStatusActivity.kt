package com.yatri

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yatri.patrol.PatrolApi
import com.yatri.patrol.PatrolStatusResponse
import com.yatri.net.Network
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.create

class PatrolStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patrol_status)

        val sessionId = intent.getStringExtra("sessionId") ?: return finish()

        lifecycleScope.launch {
            try {
                val prefs = applicationContext.dataStore.data.first()
                val roleId = prefs[PrefKeys.ACTIVE_ROLE_ID] ?: ""
                val api = Network.retrofit.create<PatrolApi>()
                val resp = api.getPatrolStatus(sessionId, roleId)
                if (resp.isSuccessful) {
                    val envelope = resp.body()
                    val patrolStatus = envelope?.data
                    if (patrolStatus != null) {
                        bind(patrolStatus)
                    } else {
                        Toast.makeText(this@PatrolStatusActivity, "No data", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@PatrolStatusActivity, "Failed to load status", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PatrolStatusActivity, e.message ?: "Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bind(data: PatrolStatusResponse) {
        findViewById<TextView>(R.id.badgeStatus).text = data.status
        findViewById<TextView>(R.id.tvTotal).text = data.totalCheckpoints.toString()
        findViewById<TextView>(R.id.tvScanned).text = data.scannedCheckpointsCount.toString()
        findViewById<TextView>(R.id.tvRemaining).text = data.remainingCheckpointsCount.toString()

        val listScanned = findViewById<LinearLayout>(R.id.listScanned)
        val listRemaining = findViewById<LinearLayout>(R.id.listRemaining)
        val emptyScanned = findViewById<TextView>(R.id.emptyScanned)

        listScanned.removeAllViews()
        listRemaining.removeAllViews()

        if (data.scannedCheckpoints.isEmpty()) {
            emptyScanned.visibility = View.VISIBLE
        } else {
            emptyScanned.visibility = View.GONE
            data.scannedCheckpoints.forEach { cp ->
                listScanned.addView(makeRow(cp.checkpoint_name))
            }
        }

        data.remainingCheckpoints.forEach { cp ->
            listRemaining.addView(makeRow(cp.checkpoint_name))
        }
    }

    private fun makeRow(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setPadding(16, 16, 16, 16)
        tv.background = getDrawable(R.drawable.bg_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 12
        tv.layoutParams = lp
        return tv
    }
}


