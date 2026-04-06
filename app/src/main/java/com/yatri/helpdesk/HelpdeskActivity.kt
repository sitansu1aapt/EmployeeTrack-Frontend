package com.yatri.helpdesk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yatri.R

class HelpdeskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_helpdesk)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.helpdeskContainer, HelpdeskFragment())
                .commit()
        }
    }
}
