# EmployeeTrack-Frontend - Complete Codebase Guide

## 📱 Project Overview
This is an **Android employee tracking application** built with Kotlin, designed to manage attendance, tasks, location tracking, and employee alerts.

---

## 🏗️ Architecture & Entry Points

### Application Entry Point: MyApp.kt
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.context = applicationContext
        SleepAlertManager.initialize(this)  // Initialize sleep alert system
    }
}
```
**Purpose**: Custom Application class that initializes global context and sleep alert functionality.

---

## 📂 Activity Flow (User Journey)

### 1. **SplashActivity** → Launcher (First Screen)
**File**: `SplashActivity.kt`

**What it does**:
- Displays splash screen for 1.5 seconds
- Initializes localization (English/Odia)
- Loads authentication token from DataStore
- Checks if user has valid token

**Flow**:
```
SplashActivity (1.5 sec delay)
    ↓
Has valid token? 
    ├─ YES → EmployeeActivity (Main App)
    └─ NO → LoginActivity (Sign In)
```

**Key Code**:
```kotlin
val tok = applicationContext.dataStore.data.first()[PrefKeys.AUTH_TOKEN]
if (!tok.isNullOrEmpty()) {
    startActivity(Intent(this, EmployeeActivity::class.java))
} else {
    startActivity(Intent(this, LoginActivity::class.java))
}
```

---

### 2. **LoginActivity** → Authentication Screen
**File**: `LoginActivity.kt`

**Features**:
- 🌐 Language selection (English/Odia)
- 📧 Multiple login methods:
  - Employee ID
  - Email
  - Phone Number
- Password authentication
- Remember me checkbox
- Password reset option

**UI Elements**:
- Dropdown for login method selection
- EditText fields for credentials
- Language selector dropdown

**Authentication Flow**:
```kotlin
val request = LoginRequest(
    organizationId = 1,
    identifierType = "empId",  // or "email" or "phone"
    empId = identifier,
    password = password
)
authApi.login(request)  // Returns auth token
TokenStore.token = token  // Save globally
```

---

### 3. **EmployeeActivity** → Main App Container
**File**: `EmployeeActivity.kt`

**Architecture**: Fragment-based with Bottom Navigation

**Layout**: `activity_employee.xml`

**Navigation Tabs** (Bottom Navigation Bar):
```
┌─────────────────────────────────────────┐
│  Dashboard  │  Tasks  │  Messages │  Profile │
├─────────────────────────────────────────┤
│         Fragment Container               │
│    (Changes based on selected tab)       │
└─────────────────────────────────────────┘
```

**Code**:
```kotlin
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
```

---

## 🎨 Fragment Details

### 🏠 **DashboardFragment** → Home Screen
**File**: `DashboardFragment.kt`  
**Layout**: `fragment_dashboard.xml`

**Features**:
- ✅ Check-in/Check-out functionality
- ⏱️ Duty timer (tracks work hours)
- 📍 Location tracking toggle
- 🗺️ Google Maps integration
- 📊 Duty status display

**Key Functions**:

```kotlin
// Check-in functionality
private fun checkIn() {
    // Records current timestamp
    // Sends location data to API
    // Shows check-in confirmation
}

// Check-out functionality
private fun checkOut() {
    // Stops duty timer
    // Records end time
    // Calculates total work hours
}

// Background location tracking
private fun toggleBackgroundTracking(enabled: Boolean) {
    if (enabled) {
        startService(Intent(context, LocationService::class.java))
    }
}
```

**Permissions Required**:
- Location (Fine & Coarse)
- Background Location

---

### ✅ **TasksFragment** → Task Management
**File**: `tasks/TasksFragment.kt`  
**Layout**: `fragment_tasks.xml`

**Features**:
- 📋 View assigned tasks
- 🔄 Update task status (Pending → In Progress → Completed)
- 🔍 Filter tasks by status
- 📊 Sort tasks by priority/date
- 📱 RecyclerView with task cards

**Data Flow**:
```kotlin
// Load tasks from API
tasksApi.getTasks()
    ↓
// Filter/Sort based on user selection
applyFiltersAndSort()
    ↓
// Update RecyclerView adapter
adapter.updateList(filteredTasks)
```

**Task Status Flow**:
```
TasksAdapter.onStatusUpdate()
    ↓
updateTaskStatus(task.id, newStatus)
    ↓
Send API request
    ↓
Update UI
```

---

### 💬 **MessagesFragment** → Communications
**File**: `MessagesFragment.kt`  
**Layout**: `fragment_messages.xml`

**Features**:
- 📨 View notifications/messages
- 🔔 Push notifications via Firebase Cloud Messaging
- 📝 Message details

---

### 👤 **ProfileFragment** → User Profile
**File**: `ProfileFragment.kt`  
**Layout**: `fragment_profile.xml`

**Features**:
- 👤 Display user information
- 🔐 Logout functionality
- 📋 Profile settings
- 🌐 Language preferences

---

## 🔐 Authentication & Storage

### Token Management
**File**: `TokenStore.kt`

```kotlin
object TokenStore {
    var token: String = ""  // Holds JWT token in memory
}
```

### Persistent Storage
**File**: `PrefKeys.kt`

Uses **DataStore** (modern replacement for SharedPreferences):
```kotlin
object PrefKeys {
    val AUTH_TOKEN = stringPreferencesKey("auth_token")
    val ACTIVE_ROLE_ID = stringPreferencesKey("active_role_id")
    val USER_ID = stringPreferencesKey("user_id")
    // ... other keys
}
```

**Usage**:
```kotlin
// Save token
lifecycleScope.launch {
    context.dataStore.edit { prefs ->
        prefs[PrefKeys.AUTH_TOKEN] = token
    }
}

// Load token
lifecycleScope.launch {
    val token = context.dataStore.data.first()[PrefKeys.AUTH_TOKEN]
}
```

---

## 🌐 Network & API Integration

### Network Configuration
**File**: `net/Network.kt`

```kotlin
object Network {
    val retrofit: Retrofit = /* initialization */
}
```

### API Interfaces

**AuthApi** - Authentication
```kotlin
interface AuthApi {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
```

**TasksApi** - Task Management
```kotlin
interface TasksApi {
    @GET("/tasks")
    suspend fun getTasks(): List<AssignedTask>
    
    @PUT("/tasks/{id}/status")
    suspend fun updateTaskStatus(@Path("id") id: String, @Body status: StatusUpdate)
}
```

**ProfileApi** - User Profile
```kotlin
interface ProfileApi {
    @GET("/profile")
    suspend fun getProfile(): UserProfile
}
```

---

## 🛠️ Key Services & Managers

### LocationService
**File**: `LocationService.kt`

Runs as a **Foreground Service** to track user location in background.

```kotlin
class LocationService : Service() {
    // Continuously fetches GPS location
    // Sends to API periodically
    // Shows persistent notification
}
```

### SleepAlertManager
**File**: `SleepAlertManager.kt`

Monitors for driver/employee drowsiness:
- 🚨 Detects inactive periods
- 📢 Triggers alerts (sound + notification)
- 💤 Prompts alertness quiz

### FirebaseMessaging Service
**File**: `fcm/MyMessagingService.kt`

Handles push notifications:
- 🔔 Receives notifications from backend
- 🎯 Directs to appropriate activity

---

## 📍 Special Features

### Check-in Process
**File**: `checkin/CheckInActivity.kt`

Steps:
1. 📸 Capture selfie for verification
2. 📍 Confirm location
3. ✅ Submit check-in

### Patrol System
**File**: `PatrolDashboardActivity.kt`, `ActivePatrolActivity.kt`

- 🚨 Real-time patrol status
- ⏱️ Elapsed time tracking
- 📍 Route tracking

### Attendance History
**File**: `attendance/AttendanceHistoryActivity.kt`

- 📊 Historical attendance records
- 📈 Monthly reports
- 📅 Attendance statistics

---

## 🎯 UI Layout Structure

### Main Layouts:
```
res/layout/
├── activity_employee.xml         → Main container (BottomNav + Fragment)
├── fragment_dashboard.xml        → Dashboard UI
├── fragment_tasks.xml            → Tasks RecyclerView
├── fragment_messages.xml         → Messages list
├── fragment_profile.xml          → User profile
├── activity_login.xml            → Login form
├── activity_splash.xml           → Splash screen
└── ... (other specialized layouts)
```

### RecyclerView Items:
```
├── item_task.xml                 → Task card item
├── item_notification.xml         → Notification card
└── item_attendance.xml           → Attendance row
```

---

## 🚀 How to Build & Run

### Prerequisites:
- ✅ Android Studio (Latest)
- ✅ Android SDK (API 24+)
- ✅ Java 8+
- ✅ Physical device or emulator with API 24+

### Build Options:

#### 1️⃣ **Build Debug APK** (Development)
```bash
./gradlew assembleDebug
```
**Output**: `app/build/outputs/apk/debug/app-universal-debug.apk`

#### 2️⃣ **Build Release APK** (Production)
```bash
./gradlew assembleRelease
```
**Output**: Signed with keystore credentials
**Signing Config**: 
```
Keystore: yatri.jks
Store Password: Flutter2@
Key Alias: key0
Key Password: Flutter2@
```

#### 3️⃣ **Build & Install** (VS Code Tasks)
```bash
# Run "Build and Install" task which:
# 1. Builds debug APK
# 2. Installs on connected device/emulator
```

#### 4️⃣ **Clean Build** (Remove build artifacts)
```bash
./gradlew clean assembleDebug
```

---

## 🔧 Running the App

### Method 1: Android Studio
```
1. Open project in Android Studio
2. Select device/emulator from toolbar
3. Click "Run" (Green play button)
4. App launches automatically
```

### Method 2: Command Line
```bash
# Install debug APK on device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# Run app
adb shell am start -n com.yatri/.SplashActivity
```

### Method 3: VS Code Tasks
```
1. Open Command Palette (Ctrl+Shift+P)
2. Run task "Build Debug APK"
3. Run task "Install Debug APK"
4. Or run combined "Build and Install" task
```

---

## 📋 Logging & Debugging

### View Logcat Output:
```bash
# Filtered logs (specific components)
adb logcat -s TasksFragment:D TasksAdapter:D

# All logs
adb logcat

# Filter by tag
adb logcat -s DashboardFragment:D

# Clear logs
adb logcat -c
```

### Debug Logs in Code:
```kotlin
android.util.Log.d("TAG", "Message")    // Debug
android.util.Log.e("TAG", "Error")      // Error
android.util.Log.w("TAG", "Warning")    // Warning
```

---

## 📦 Dependencies

### Key Libraries:
```
- Retrofit 2: HTTP client for APIs
- Gson: JSON serialization
- Kotlin Serialization: Object serialization
- FirebaseMessaging: Push notifications
- CameraX: Camera functionality
- Google Maps: Map display
- DataStore: Secure preferences storage
- Coroutines: Async operations
- Lifecycle: ViewModel & LiveData
```

---

## 🔐 Permissions Required

```xml
<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Camera -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Location Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Device Control (Wake Lock, Screen On) -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.TURN_SCREEN_ON" />
```

---

## 📱 App Configuration

**File**: `AppConfig.kt`

```kotlin
object AppConfig {
    const val API_BASE_URL = "https://api.example.com/"  // Backend API URL
    const val APP_VERSION = "1.2.6"
}
```

---

## 🎓 Code Flow Examples

### Example 1: Login Flow
```
1. User enters Employee ID & Password in LoginActivity
2. User clicks Login button
3. Network request sent: authApi.login(LoginRequest)
4. Backend validates credentials
5. Returns JWT token + user info
6. Token saved to DataStore & TokenStore
3. Navigate to EmployeeActivity
7. EmployeeActivity loads token from DataStore
8. Token used in Authorization header for all API calls
```

### Example 2: Task Update Flow
```
1. User views Tasks in TasksFragment
2. TasksAdapter displays task list via RecyclerView
3. User clicks "Update Status" button on task
4. handleStatusUpdate() called with task object
5. Network request: tasksApi.updateTaskStatus(taskId, newStatus)
6. Backend updates database
7. Response returns success
8. UI updates: task status changed in RecyclerView
9. Adapter refreshes display
```

### Example 3: Location Tracking Flow
```
1. User toggles "Background Tracking" switch in Dashboard
2. If enabled:
   - Request location permission
   - Start LocationService (Foreground Service)
   - Show persistent notification
   - Schedule periodic location updates
   - Send locations to API backend
3. If disabled:
   - Stop LocationService
   - Stop location updates
   - Remove notification
```

---

## 🐛 Troubleshooting

### App Crashes on Start?
- Check token validity in DataStore
- Verify API connectivity
- Check for missing permissions

### API Calls Failing?
- Verify token is loaded correctly
- Check network connection
- Review Retrofit configuration
- Check API base URL in AppConfig

### Tasks Not Loading?
- Verify roleId is set in DataStore
- Check API response in Logcat
- Confirm Authorization header is present

### Location Not Working?
- Confirm location permissions granted
- Check GPS enabled on device
- Verify LocationService is running
- Check Google Play Services installed

---

## 📞 API Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/login` | POST | User authentication |
| `/tasks` | GET | Fetch user tasks |
| `/tasks/{id}/status` | PUT | Update task status |
| `/profile` | GET | Fetch user profile |
| `/attendance/checkin` | POST | Record check-in |
| `/attendance/checkout` | POST | Record check-out |
| `/location/track` | POST | Send location data |

---

## 🎯 Development Workflow

1. **Start App**: Run debug build via Android Studio or gradle task
2. **Test Feature**: Navigate through UI, interact with components
3. **View Logs**: Use Logcat to monitor execution
4. **Make Changes**: Edit Kotlin files
5. **Rebuild**: Run gradle tasks or click Run in Android Studio
6. **Repeat**: Test again until working correctly

---

**Version**: 1.2.6  
**Requires**: Android 7.0 (API 24) or higher  
**Target**: Android 14 (API 34)

