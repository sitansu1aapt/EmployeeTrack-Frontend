package com.yatri

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class EmployeeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_dashboard -> switch(DashboardFragment())
                R.id.tab_tasks -> switch(TasksFragment())
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


