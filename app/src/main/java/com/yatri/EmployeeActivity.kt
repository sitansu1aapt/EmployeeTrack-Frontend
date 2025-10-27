package com.yatri

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.google.android.material.bottomnavigation.BottomNavigationView

class EmployeeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee)

        // Ensure token is loaded from DataStore before any API calls
        lifecycleScope.launch {
            val tok = applicationContext.dataStore.data.first()[com.yatri.PrefKeys.AUTH_TOKEN]
            if (!tok.isNullOrEmpty()) {
                com.yatri.TokenStore.token = tok
                android.util.Log.d("EmployeeActivity", "Token loaded in EmployeeActivity: $tok")
            } else {
                android.util.Log.d("EmployeeActivity", "No token found in DataStore")
            }
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_dashboard -> switch(DashboardFragment())
                R.id.tab_tasks -> switch(com.yatri.tasks.TasksFragment())
                R.id.tab_messages -> switch(MessagesFragment())
                R.id.tab_profile -> switch(ProfileFragment())
            }
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.tab_dashboard
        }
    }

    private fun switch(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}


