package com.yatri.helpdesk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.yatri.R
import kotlinx.coroutines.launch
import com.yatri.net.Network

class HelpdeskFragment : Fragment() {
    private val helpdeskApi by lazy { Network.retrofit.create(HelpdeskApi::class.java) }
    private lateinit var adapter: HelpdeskAdapter
    private lateinit var rvRequests: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var btnCreate: Button

    private lateinit var createRequestLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createRequestLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                loadRequests()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_helpdesk, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvRequests = view.findViewById(R.id.rvHelpdeskRequests)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        tvEmpty = view.findViewById(R.id.tvEmptyState)
        btnCreate = view.findViewById(R.id.btnCreateRequest)

        adapter = HelpdeskAdapter(emptyList()) { item ->
            startActivity(Intent(requireContext(), HelpdeskDetailActivity::class.java).apply {
                putExtra(HelpdeskDetailActivity.EXTRA_REQUEST_ID, item.helpdesk_request_id)
            })
        }
        rvRequests.layoutManager = LinearLayoutManager(requireContext())
        rvRequests.adapter = adapter

        btnCreate.setOnClickListener {
            createRequestLauncher.launch(Intent(requireContext(), CreateHelpdeskRequestActivity::class.java))
        }

        swipeRefresh.setOnRefreshListener { loadRequests(refresh = true) }
        swipeRefresh.setColorSchemeResources(R.color.blue_500)

        loadRequests()
    }

    private fun loadRequests(refresh: Boolean = false) {
        swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = helpdeskApi.getRequests(limit = 50, offset = 0)
                if (response.success && response.data != null) {
                    val requests = response.data.requests
                    adapter.updateData(requests)
                    tvEmpty.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    showError(response.message.ifEmpty { getString(R.string.unable_to_load_requests) })
                }
            } catch (e: Exception) {
                android.util.Log.e("HelpdeskFragment", "loadRequests error", e)
                showError(getString(R.string.network_error))
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }
}
