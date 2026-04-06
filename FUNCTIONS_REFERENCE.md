# Key Functions & How They Work

## 🔐 Authentication Flow Functions

### LoginActivity.kt
```kotlin
btnLogin.setOnClickListener {
    lifecycleScope.launch {
        // 1. Get user input
        val identifier = etIdentifier.text?.toString()?.trim()
        val password = etPassword.text?.toString()?.trim()
        
        // 2. Validate
        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(this@LoginActivity, "Enter credentials", Toast.LENGTH_SHORT).show()
            return@launch
        }
        
        // 3. Determine login method
        val identifierType = when (acType.text?.toString()) {
            "Employee ID" → "empId"
            "Email" → "email"
            else → "phone"
        }
        
        // 4. Create request object
        val request = LoginRequest(
            organizationId = 1,
            identifierType = identifierType,
            empId = if (identifierType == "empId") identifier else null,
            email = if (identifierType == "email") identifier else null,
            phone = if (identifierType == "phone") identifier else null,
            password = password
        )
        
        // 5. Call API
        val response = authApi.login(request)
        
        // 6. Save token to persistent storage
        context.dataStore.edit { prefs →
            prefs[PrefKeys.AUTH_TOKEN] = response.token
        }
        
        // 7. Save token to memory
        TokenStore.token = response.token
        
        // 8. Navigate to main app
        startActivity(Intent(this, EmployeeActivity::class.java))
        finish()
    }
}
```

**What it does**: Authenticates user with email/ID/phone, saves token, redirects to app

---

## 📍 Location & Tracking Functions

### DashboardFragment.kt - Check-In
```kotlin
private fun checkIn() {
    // 1. Request location permission if needed
    if (ContextCompat.checkSelfPermission(requireContext(),
        Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            permReq)
        return
    }
    
    // 2. Get current location
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    fusedLocationClient.lastLocation.addOnSuccessListener { location →
        if (location != null) {
            // 3. Prepare check-in data
            val lat = location.latitude
            val lng = location.longitude
            val timestamp = System.currentTimeMillis()
            
            // 4. Create JSON payload
            val json = """
            {
                "action": "check_in",
                "latitude": $lat,
                "longitude": $lng,
                "timestamp": $timestamp
            }
            """.trimIndent()
            
            // 5. Send API request
            lifecycleScope.launch {
                try {
                    val body = json.toRequestBody("application/json".toMediaType())
                    val response = api.checkIn(body)
                    
                    // 6. Update UI
                    isOnDuty = true
                    tvDutyStatus.text = "ON DUTY"
                    dutyStartTime = System.currentTimeMillis()
                    startTimer()
                    
                    Toast.makeText(requireContext(), "Checked In", Toast.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
```

**What it does**: Gets GPS location, records check-in time, sends to backend, starts duty timer

---

### DashboardFragment.kt - Location Tracking Toggle
```kotlin
private fun toggleBackgroundTracking(enabled: Boolean) {
    if (enabled) {
        // 1. Request all location permissions
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        
        if (ContextCompat.checkSelfPermission(requireContext(),
            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, permReq)
            return
        }
        
        // 2. Start LocationService (foreground service)
        val intent = Intent(requireContext(), LocationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
        
        // 3. Save tracking state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_TRACKING, true).apply()
        
        // 4. Update UI
        tvBackgroundStatus.text = "Tracking: ACTIVE"
        
    } else {
        // 1. Stop the service
        val intent = Intent(requireContext(), LocationService::class.java)
        requireContext().stopService(intent)
        
        // 2. Save state
        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_TRACKING, false).apply()
        
        // 3. Update UI
        tvBackgroundStatus.text = "Tracking: INACTIVE"
    }
}
```

**What it does**: Starts or stops background GPS tracking service

---

## ✅ Task Management Functions

### TasksFragment.kt - Load Tasks
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    // 1. Setup RecyclerView
    val rv = view.findViewById<RecyclerView>(R.id.rvTasks)
    adapter = TasksAdapter(emptyList()) { task →
        handleStatusUpdate(task)  // Click handler
    }
    rv.layoutManager = LinearLayoutManager(requireContext())
    rv.adapter = adapter
    
    // 2. Load roleId from DataStore
    viewLifecycleOwner.lifecycleScope.launch {
        val prefs = requireContext().dataStore.data.first()
        roleId = prefs[PrefKeys.ACTIVE_ROLE_ID]
        
        // 3. Fetch tasks from API
        try {
            progressBar.visibility = View.VISIBLE
            val tasks = tasksApi.getTasks()  // GET /tasks
            
            // 4. Store all tasks
            allTasks = tasks
            
            // 5. Apply filter/sort and update adapter
            applyFiltersAndSort()
            
            // 6. Show/hide empty state
            if (tasks.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rv.visibility = View.VISIBLE
            }
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to load tasks", Toast.LENGTH_SHORT).show()
        } finally {
            progressBar.visibility = View.GONE
        }
    }
}
```

**What it does**: Fetches tasks from API, displays in RecyclerView, handles empty state

---

### TasksFragment.kt - Update Task Status
```kotlin
private fun handleStatusUpdate(task: AssignedTask) {
    // 1. Determine next status
    val newStatus = when (task.taskStatus) {
        "PENDING" → "IN_PROGRESS"
        "IN_PROGRESS" → "COMPLETED"
        else → "PENDING"
    }
    
    // 2. Create JSON payload
    val json = """
    {
        "taskId": "${task.taskId}",
        "status": "$newStatus"
    }
    """.trimIndent()
    
    // 3. Send API request
    lifecycleScope.launch {
        try {
            val body = json.toRequestBody("application/json".toMediaTypeOrNull())
            val response = tasksApi.updateTaskStatus(task.taskId, body)
            
            // 4. Update task in local list
            val updatedTask = task.copy(taskStatus = newStatus)
            val index = allTasks.indexOfFirst { it.taskId == task.taskId }
            if (index >= 0) {
                allTasks = allTasks.toMutableList().apply {
                    set(index, updatedTask)
                }
            }
            
            // 5. Update RecyclerView adapter
            applyFiltersAndSort()
            
            Toast.makeText(requireContext(), "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

**What it does**: Updates task status (PENDING → IN_PROGRESS → COMPLETED)

---

## 🔔 Notification Functions

### MyMessagingService.kt - Receive Push Notification
```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    // 1. Check if notification data exists
    if (remoteMessage.notification != null) {
        val title = remoteMessage.notification!!.title ?: "Notification"
        val body = remoteMessage.notification!!.body ?: ""
        
        // 2. Check if contains data payload
        val data = remoteMessage.data
        val action = data["action"] ?: "default"
        val taskId = data["taskId"]
        val taskTitle = data["taskTitle"]
        
        // 3. Create notification channel (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "alerts",
                "Task Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        // 4. Create notification intent
        val intent = Intent(this, NotificationDetailActivity::class.java).apply {
            putExtra("taskId", taskId)
            putExtra("title", taskTitle)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 5. Build and display notification
        val notification = NotificationCompat.Builder(this, "alerts")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
```

**What it does**: Receives FCM push notification, creates notification, launches activity on click

---

## 👤 Profile Functions

### ProfileFragment.kt - Logout
```kotlin
private fun logout() {
    lifecycleScope.launch {
        // 1. Clear token from DataStore
        context?.dataStore?.edit { prefs →
            prefs[PrefKeys.AUTH_TOKEN] = ""
            prefs[PrefKeys.USER_ID] = ""
            prefs[PrefKeys.ACTIVE_ROLE_ID] = ""
        }
        
        // 2. Clear token from memory
        TokenStore.token = ""
        
        // 3. Stop background services
        val locationIntent = Intent(context, LocationService::class.java)
        context?.stopService(locationIntent)
        
        // 4. Navigate to login
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        
        // 5. Close EmployeeActivity
        requireActivity().finish()
    }
}
```

**What it does**: Clears stored credentials, stops services, redirects to login

---

## ⏱️ Timer Functions

### DashboardFragment.kt - Duty Timer
```kotlin
private fun startTimer() {
    timerHandler = Handler(Looper.getMainLooper())
    timerRunnable = object : Runnable {
        override fun run() {
            if (isOnDuty) {
                // 1. Calculate elapsed time
                val elapsedMillis = System.currentTimeMillis() - dutyStartTime
                val hours = (elapsedMillis / 3600000).toInt()
                val minutes = ((elapsedMillis % 3600000) / 60000).toInt()
                val seconds = ((elapsedMillis % 60000) / 1000).toInt()
                
                // 2. Format time
                val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                
                // 3. Update UI
                tvDutyTimer.text = "Duty Time: $timeString"
                
                // 4. Schedule next update
                timerHandler?.postDelayed(this, 1000)  // Update every 1 second
            }
        }
    }
    timerHandler?.post(timerRunnable!!)
}

private fun stopTimer() {
    timerHandler?.removeCallbacks(timerRunnable!!)
    timerHandler = null
    timerRunnable = null
}
```

**What it does**: Shows running timer of duty hours, updates every second

---

## 📱 RecyclerView & Adapter

### TasksAdapter.kt - Item Binding
```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val task = tasks[position]
    
    // 1. Bind task data to UI
    holder.tvTitle.text = task.title
    holder.tvDescription.text = task.description
    holder.tvStatus.text = task.taskStatus
    holder.tvPriority.text = "Priority: ${task.priority}"
    
    // 2. Color code by status
    val statusColor = when (task.taskStatus) {
        "PENDING" → Color.RED
        "IN_PROGRESS" → Color.YELLOW
        "COMPLETED" → Color.GREEN
        else → Color.GRAY
    }
    holder.tvStatus.setTextColor(statusColor)
    
    // 3. Set click listener on status button
    holder.btnUpdateStatus.setOnClickListener {
        onStatusUpdate(task)  // Call lambda function
    }
}
```

**What it does**: Binds task data to RecyclerView item, handles clicks

---

## 🔄 Coroutine & Error Handling

```kotlin
// Standard pattern for API calls
lifecycleScope.launch {
    try {
        // 1. Show loading
        progressBar.visibility = View.VISIBLE
        
        // 2. Make API call
        val response = api.getData()
        
        // 3. Update UI with response
        adapter.updateList(response)
        
    } catch (e: HttpException) {
        // 4. Handle HTTP errors
        Toast.makeText(context, "Server error: ${e.code()}", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        // 5. Handle network errors
        Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // 6. Handle unknown errors
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        // 7. Hide loading
        progressBar.visibility = View.GONE
    }
}
```

**What it does**: Safe API call with error handling and UI feedback

---

## 🌐 Network Interceptor

```kotlin
// OkHttp Interceptor in Network.kt
val httpClient = OkHttpClient.Builder()
    .addInterceptor { chain →
        val originalRequest = chain.request()
        
        // 1. Get token from memory
        val token = TokenStore.token
        
        // 2. Create new request with Authorization header
        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        
        // 3. Execute request with header
        chain.proceed(request)
    }
    .build()
```

**What it does**: Automatically adds token to every API request

---

## Summary: Function Execution Flow

```
User Action (Click Button)
    ↓
Event Listener Triggered
    ↓
Get User Input / Permission Check
    ↓
Make API Call (lifecycleScope.launch)
    ↓
Try/Catch Error Handling
    ↓
Update Local Data Structure
    ↓
Update UI (RecyclerView, TextView, etc)
    ↓
Success/Error Toast Message
    ↓
Function Complete
```

**All functions follow this pattern for consistency and error handling.**

