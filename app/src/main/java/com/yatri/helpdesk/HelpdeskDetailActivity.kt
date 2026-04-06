package com.yatri.helpdesk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.yatri.R
import com.yatri.net.Network
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HelpdeskDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
    }

    private lateinit var tvQueryType: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvSite: TextView
    private lateinit var tvDepartment: TextView
    private lateinit var btnAttachment: Button
    private lateinit var replyContainer: LinearLayout
    private lateinit var tvNoReplies: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_helpdesk_detail)

        tvQueryType = findViewById(R.id.tvDetailQueryType)
        tvStatus = findViewById(R.id.tvDetailStatus)
        tvDescription = findViewById(R.id.tvDetailDescription)
        tvSite = findViewById(R.id.tvDetailSite)
        tvDepartment = findViewById(R.id.tvDetailDepartment)
        btnAttachment = findViewById(R.id.btnDetailAttachment)
        replyContainer = findViewById(R.id.replyContainer)
        tvNoReplies = findViewById(R.id.tvNoReplies)
        progressBar = findViewById(R.id.progressBar)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.helpdesk_request)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        if (requestId < 0) {
            finish()
            return
        }

        loadDetail(requestId)
    }

    private fun loadDetail(requestId: Long) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = Network.retrofit.create(HelpdeskApi::class.java)
                val response = api.getRequestDetail(requestId)
                if (response.success && response.data != null) {
                    bindDetail(response.data)
                } else {
                    showToast(response.message.ifEmpty { getString(R.string.unable_to_load_detail) })
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("HelpdeskDetail", "loadDetail error", e)
                showToast(getString(R.string.network_error))
                finish()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun bindDetail(detail: HelpdeskRequestDetail) {
        tvQueryType.text = detail.query_type.replace('_', ' ')
        tvStatus.text = detail.status
        tvDescription.text = detail.description
        tvSite.text = detail.site_name ?: getString(R.string.not_available)
        tvDepartment.text = detail.department_name ?: getString(R.string.not_available)

        if (!detail.attachment_url.isNullOrBlank()) {
            btnAttachment.visibility = View.VISIBLE
            btnAttachment.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.attachment_url))
                startActivity(intent)
            }
        } else {
            btnAttachment.visibility = View.GONE
        }

        replyContainer.removeAllViews()
        if (detail.replies.isEmpty()) {
            tvNoReplies.visibility = View.VISIBLE
        } else {
            tvNoReplies.visibility = View.GONE
            detail.replies.forEach { reply ->
                val view = LayoutInflater.from(this).inflate(R.layout.item_helpdesk_reply, replyContainer, false)
                view.findViewById<TextView>(R.id.tvReplySender).text = reply.sender_name ?: reply.sender_role_name
                view.findViewById<TextView>(R.id.tvReplyRole).text = reply.sender_role_name
                view.findViewById<TextView>(R.id.tvReplyMessage).text = reply.reply_message ?: "No message"
                view.findViewById<TextView>(R.id.tvReplyStatus).text = reply.status_after_reply
                view.findViewById<TextView>(R.id.tvReplyCreatedAt).text = HelpdeskAdapter.formatTimestamp(reply.created_at)
                val btnReplyAttachment = view.findViewById<Button>(R.id.btnReplyAttachment)
                if (!reply.attachment_url.isNullOrBlank()) {
                    btnReplyAttachment.visibility = View.VISIBLE
                    btnReplyAttachment.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reply.attachment_url)))
                    }
                } else {
                    btnReplyAttachment.visibility = View.GONE
                }
                replyContainer.addView(view)
            }
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
