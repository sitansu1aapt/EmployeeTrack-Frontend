# EmployeeTrack-Frontend: Complete Summary

## 📱 What is This App?

**EmployeeTrack-Frontend** is an Android employee management application that helps companies track:
- ✅ Employee attendance (Check-in/Check-out)
- 📍 Real-time location tracking
- ✅ Task assignment & status management
- 💬 Push notifications & messages
- 💤 Driver alertness/drowsiness monitoring
- 📊 Attendance history & reports

---

## 🎯 How to Run the App (Complete Steps)

### Prerequisites:
1. ✅ Android Studio installed
2. ✅ Android SDK (API 24+)
3. ✅ Device or Emulator with Android 7.0+
4. ✅ USB debugging enabled (for physical device)

### Step-by-Step:

#### **Option 1: Using Android Studio** (Easiest)
```
1. Open project: File → Open → Select EmployeeTrack-Frontend folder
2. Wait for Gradle build to complete
3. Connect device via USB or open emulator
4. Click green "Run" button (▶) in toolbar
5. Select device/emulator
6. Click "Run"
7. App launches automatically on device
```

#### **Option 2: Using Command Line**
```bash
# Navigate to project folder
cd e:\AATPL\Yatri\EmployeeTrack-Frontend

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# Launch app
adb shell am start -n com.yatri/.SplashActivity

# View logs
adb logcat | grep -E "MainActivity|TasksFragment|DashboardFragment"
```

#### **Option 3: Using VS Code Tasks**
```
1. Open VS Code in the project folder
2. Press Ctrl+Shift+P (Command Palette)
3. Type: "Run Task"
4. Select: "Build Debug APK"
5. Wait for build to complete
6. Select: "Install Debug APK"
7. Connect device and run
```

---

## 🏗️ Complete Application Architecture

### **Entry Point: MyApp.kt**
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        AppContext.context = applicationContext  // Global context
        SleepAlertManager.initialize(this)       // Sleep alert system
    }
}
```
**Runs**: When app process is created

---

### **Screen 1: SplashActivity** (First Screen on Launch)
**Purpose**: Show loading screen, check authentication

**What happens**:
1. Displays splash screen for 1.5 seconds
2. Initializes multi-language support
3. Loads auth token from disk (DataStore)
4. Checks if token is valid

**Decision Logic**:
```
Token exists?
├─ YES → Jump to EmployeeActivity (Main App)
└─ NO → Jump to LoginActivity (Sign In)
```

---

### **Screen 2: LoginActivity** (Sign In Screen)
**Purpose**: Authenticate user

**UI Components**:
- 🌐 Language selector (English/Odia)
- 📋 Login method dropdown (Employee ID / Email / Phone Number)
- 📧 Identifier input field
- 🔐 Password field
- ☑️ Remember me checkbox
- 🔑 Login button
- 🔓 Forgot password link

**Authentication Flow**:
1. User selects login method (Employee ID, Email, or Phone)
2. User enters identifier & password
3. Click "Login" button
4. Send API request: `POST /auth/login`
5. Backend validates credentials
6. Backend returns JWT token (encrypted authentication key)
7. Token saved in two places:
   - **Disk (DataStore)**: Survives app restart
   - **Memory (TokenStore)**: Quick access
8. Redirect to EmployeeActivity

**Code Flow**:
```kotlin
val request = LoginRequest(
    organizationId = 1,
    identifierType = "empId",  // or "email" or "phone"
    empId = identifier,
    password = password
)
response = authApi.login(request)  // API call
TokenStore.token = response.token
startActivity(Intent(this, EmployeeActivity::class.java))
```

---

### **Screen 3: EmployeeActivity** (Main App Container)
**Purpose**: Container for all main app screens

**Architecture**: Fragment-based with Bottom Navigation Bar

**Layout Structure**:
```
┌─────────────────────────────────────┐
│      Status Bar & Action Bar       │
├─────────────────────────────────────┤
│                                     │
│   Fragment Container                │
│   (Changes based on tab selected)   │
│                                     │
├─────────────────────────────────────┤
│  🏠    ✅    💬    👤              │
│ Dash  Tasks  Msgs  Profile          │
│ ─────────────────────────────────── │
│ Bottom Navigation Bar (4 Tabs)     │
└─────────────────────────────────────┘
```

**Navigation Code**:
```kotlin
val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
nav.setOnItemSelectedListener { item →
    when (item.itemId) {
        R.id.tab_dashboard → switch(DashboardFragment())
        R.id.tab_tasks → switch(TasksFragment())
        R.id.tab_messages → switch(MessagesFragment())
        R.id.tab_profile → switch(ProfileFragment())
    }
    true
}
```

---

### **Tab 1: DashboardFragment** (🏠 Home Screen)
**Purpose**: Daily check-in/out and location tracking

**Features**:
1. **Duty Status Display**
   - Shows current duty status: "ON DUTY" or "OFF DUTY"
   - Shows check-in/check-out times
   
2. **Check-In Button**
   - Captures current GPS location
   - Takes selfie for identification (optional)
   - Records timestamp
   - Sends to backend
   - Starts duty timer
   
3. **Check-Out Button**
   - Records end time
   - Stops duty timer
   - Calculates total work hours
   
4. **Duty Timer** ⏱️
   - Shows hours:minutes:seconds worked
   - Updates every second
   - Shows total duty time
   
5. **Location Tracking Toggle** 📍
   - Enable/disable background GPS tracking
   - Shows tracking status
   - Continues tracking even when app is backgrounded
   
6. **Google Maps Display** 🗺️
   - Shows current location on map
   - Updates in real-time
   
7. **Duty Status Show**
   - Pending duty requests
   - Approved/rejected status

**Key Functions**:
```kotlin
checkIn()                    // Check in with location
checkOut()                   // Check out
toggleBackgroundTracking()   // Start/stop GPS tracking
setupTimer()                 // Setup duty hour timer
fetchDutyStatusAndUpdateUI() // Refresh duty status
```

---

### **Tab 2: TasksFragment** (✅ Task Management)
**Purpose**: View and manage assigned tasks

**Features**:
1. **Task List** (RecyclerView)
   - Shows all assigned tasks
   - Each task displays:
     - Task title
     - Description
     - Priority level
     - Current status
     - Due date
   
2. **Task Status States**:
   ```
   PENDING → Click to move to → IN_PROGRESS → Click to move to → COMPLETED
   ```
   
3. **Filter Tasks**
   - Filter by status: All / Pending / In Progress / Completed
   - Filter by priority: All / High / Medium / Low
   
4. **Sort Tasks**
   - Sort by date (oldest/newest)
   - Sort by priority (high to low)
   - Sort by status
   
5. **Update Task Status**
   - Click "Update" button on task
   - Sends API request to backend
   - Server updates database
   - UI refreshes with new status
   
6. **Empty State**
   - Shows "No tasks assigned" if list is empty

**API Flow**:
```kotlin
// 1. Load tasks
GET /api/tasks → Returns List<AssignedTask>

// 2. Update task status
PUT /api/tasks/{taskId}/status
{
    "status": "IN_PROGRESS" | "COMPLETED" | "PENDING"
}

// 3. UI updates
adapter.updateList(filtered_tasks)
```

---

### **Tab 3: MessagesFragment** (💬 Messages & Notifications)
**Purpose**: View notifications and alerts

**Features**:
1. **Notification List**
   - Shows all received notifications
   - Each notification shows:
     - Title
     - Message body
     - Timestamp
     - Read/unread status
   
2. **Push Notifications** (Firebase)
   - Automatically received when app is open/closed
   - Triggers alert notification on device
   - Tap notification to open details screen
   
3. **Notification Details**
   - Shows full notification content
   - May include task details
   - Actionable items
   
4. **Notification Types**:
   - Task assignments
   - Task reminders
   - Urgent alerts
   - General messages

---

### **Tab 4: ProfileFragment** (👤 User Profile & Settings)
**Purpose**: User info and app settings

**Features**:
1. **User Information**
   - Employee name
   - Employee ID
   - Department
   - Role/Position
   - Email
   - Phone number
   
2. **Settings**
   - Language preference (English/Odia)
   - Notification settings
   - Privacy settings
   
3. **Actions**
   - Change password
   - Update profile picture
   - Logout button
   
4. **Logout Functionality**
   - Clears auth token (deletes from disk & memory)
   - Stops background services
   - Redirects to LoginActivity
   - Requires re-authentication

---

## 🔐 Authentication & Token System

### Token Lifecycle:
```
1. User logs in
   ↓
2. Backend validates credentials
   ↓
3. Backend generates JWT token (like: Bearer eyJhbGc...)
   ↓
4. App saves token in TWO places:
   ├─ DataStore (persistent - survives app restart)
   └─ TokenStore (RAM - quick access)
   ↓
5. OkHttp Interceptor automatically adds token to all requests:
   Header: "Authorization: Bearer {token}"
   ↓
6. Backend receives request with token
   ↓
7. Backend validates token signature & expiration
   ↓
8. Backend processes request
   ↓
9. On logout: Token cleared from both storage locations
```

### Storage Mechanism:
```kotlin
// Save token to persistent storage
context.dataStore.edit { prefs →
    prefs[PrefKeys.AUTH_TOKEN] = "jwt_token_here"
}

// Load token from persistent storage
val token = context.dataStore.data.first()[PrefKeys.AUTH_TOKEN]

// Save token to memory for quick access
TokenStore.token = token

// Use in API call (automatic via interceptor)
// Header automatically added: Authorization: Bearer {token}
```

---

## 🌐 API Endpoints (Backend Communication)

| Endpoint | Method | Purpose | Example |
|----------|--------|---------|---------|
| `/auth/login` | POST | Authenticate user | `{ empId, password }` → Returns `{ token, userId }` |
| `/auth/logout` | POST | Logout user | Clears session |
| `/tasks` | GET | Get all tasks | Returns `List<Task>` |
| `/tasks/{id}/status` | PUT | Update task status | `{ status: "COMPLETED" }` |
| `/profile` | GET | Get user profile | Returns user info |
| `/attendance/checkin` | POST | Record check-in | `{ latitude, longitude, timestamp }` |
| `/attendance/checkout` | POST | Record check-out | `{ latitude, longitude, timestamp }` |
| `/location/track` | POST | Send GPS location | `{ latitude, longitude }` (continuous) |
| `/notifications` | GET | Get notifications | Returns list of notifications |

---

## 🛠️ Key Concepts

### Fragment
- **What**: UI container similar to Activity but lighter weight
- **Why**: Can be swapped in/out without loading new Activity
- **Used for**: All 4 tabs (Dashboard, Tasks, Messages, Profile)

### RecyclerView
- **What**: Efficient list view component
- **Why**: Handles large lists smoothly by recycling views
- **Used for**: Task list, notification list, attendance history

### Coroutine (lifecycleScope.launch)
- **What**: Lightweight thread for async operations
- **Why**: Makes network calls without blocking UI
- **Used for**: All API calls

### LiveData
- **What**: Observable data holder
- **Why**: UI automatically updates when data changes
- **Used for**: Observing API responses

### DataStore
- **What**: Modern encrypted shared preference storage
- **Why**: Secure storage for sensitive data like auth tokens
- **Used for**: Storing token, user ID, preferences

### Foreground Service
- **What**: Background service that shows persistent notification
- **Why**: Android allows background tracking only with foreground service
- **Used for**: LocationService (continuous GPS tracking)

### Firebase Cloud Messaging (FCM)
- **What**: Push notification service
- **Why**: Send real-time notifications to multiple devices
- **Used for**: Task alerts, urgent messages

---

## 📊 Directory Structure

```
EmployeeTrack-Frontend/
├── app/src/main/
│   ├── java/com/yatri/
│   │   ├── MyApp.kt                    ← Application class
│   │   ├── SplashActivity.kt           ← Splash screen
│   │   ├── LoginActivity.kt            ← Login form
│   │   ├── EmployeeActivity.kt         ← Main container
│   │   ├── DashboardFragment.kt        ← Check-in/out
│   │   ├── TasksFragment.kt            ← Task list
│   │   ├── MessagesFragment.kt         ← Notifications
│   │   ├── ProfileFragment.kt          ← User profile
│   │   ├── AppConfig.kt                ← Configuration
│   │   ├── TokenStore.kt               ← Token storage
│   │   ├── Prefs.kt                    ← Preferences
│   │   ├── LocationService.kt          ← GPS tracking
│   │   ├── SleepAlertManager.kt        ← Sleep alerts
│   │   ├── net/Network.kt              ← Retrofit config
│   │   ├── net/Api*.kt                 ← API interfaces
│   │   ├── tasks/TasksAdapter.kt       ← Task list adapter
│   │   ├── checkin/CheckInActivity.kt  ← Check-in flow
│   │   ├── patrol/PatrolStatus.kt      ← Patrol tracking
│   │   ├── fcm/MyMessagingService.kt   ← Push notifications
│   │   └── localization/...            ← Multi-language
│   ├── res/
│   │   ├── layout/                     ← XML layouts
│   │   ├── drawable/                   ← Icons & images
│   │   ├── values/                     ← Strings, colors, dimens
│   │   └── ...
│   └── AndroidManifest.xml             ← App configuration
├── build.gradle.kts                    ← Build configuration
└── README.md                           ← Project info
```

---

## 🎯 Step-by-Step Usage Example

### User Journey - First Day:

1. **Install & Open App**
   - Sees SplashActivity (1.5 sec)
   - Redirected to LoginActivity (no token)

2. **Login**
   - Selects "Employee ID"
   - Enters ID: "EMP001"
   - Enters Password: "pass123"
   - Clicks "Login"
   - Token received and saved

3. **View Dashboard**
   - Sees "OFF DUTY" status
   - Clicks "Check-In" button
   - GPS permission requested (grants permission)
   - Check-in successful, timer starts
   - "ON DUTY" status shown

4. **View Tasks**
   - Clicks Tasks tab
   - Sees list of assigned tasks
   - Task 1: "Backend API Testing" (PENDING)
   - Task 2: "UI Testing" (PENDING)
   - Clicks update on Task 1
   - Status changes to "IN_PROGRESS"

5. **Enable Location Tracking**
   - Goes to Dashboard tab
   - Toggles location tracking ON
   - Background GPS tracking starts
   - Persistent notification appears

6. **Receive Notification**
   - Push notification arrives: "Task 3 assigned"
   - Clicks notification
   - Opens NotificationDetailActivity
   - Shows new task details

7. **Check-Out**
   - Clicks "Check-Out" button
   - Status changes to "OFF DUTY"
   - Timer stops
   - Shows: "Worked 8 hours 15 minutes"

8. **View Profile**
   - Clicks Profile tab
   - Sees name, ID, department
   - Changes language to Odia
   - App UI refreshes in Odia

9. **Logout**
   - Clicks "Logout" button
   - Token deleted
   - Returns to LoginActivity

---

## 🚀 Build & Run Commands

```bash
# Build debug version
./gradlew assembleDebug

# Build release version (production)
./gradlew assembleRelease

# Clean previous builds
./gradlew clean

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# Uninstall app
adb uninstall com.yatri

# View app logs
adb logcat

# Clear logs
adb logcat -c

# Filter logs by tag
adb logcat | grep DashboardFragment

# Start app from command line
adb shell am start -n com.yatri/.SplashActivity

# Check connected devices
adb devices
```

---

## ✅ Permissions Required

```xml
<!-- Network connection -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- GPS tracking -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Camera for selfies -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Background services -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Device controls -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.TURN_SCREEN_ON" />
```

---

## 📚 Documentation Files Created

1. **CODEBASE_GUIDE.md** - Comprehensive code explanation with all entry points, functions, UI layout, architecture
2. **QUICK_START.md** - 5-minute quick start guide, build tasks, common issues
3. **ARCHITECTURE.md** - Detailed architecture diagrams, data flow, component relationships
4. **FUNCTIONS_REFERENCE.md** - Code examples for all key functions with detailed explanations
5. **SUMMARY.md** (this file) - Complete overview and usage guide

---

## 🎓 Learning Path

**Start with:**
1. Read this SUMMARY.md (you are here)
2. Look at QUICK_START.md to build and run

**Then explore:**
3. CODEBASE_GUIDE.md for detailed code explanations
4. ARCHITECTURE.md for understanding data flow
5. FUNCTIONS_REFERENCE.md for code examples

**Finally:**
6. Open actual source files in Android Studio
7. Run app on device
8. Test all features
9. Try making small changes
10. Build and run again

---

## 🐛 Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| App crashes on start | No token or invalid token | Login first, check network |
| Tasks not loading | Missing permission or API error | Grant location permission, check logs |
| Location not updating | Service not started | Toggle tracking switch, check permissions |
| Notifications not working | FCM not configured | Check Google Play Services, verify API key |
| Build fails | Gradle sync issue | Run `./gradlew clean` then rebuild |
| Token expired | Session timed out on backend | Logout and login again |

---

**Version**: 1.2.6  
**Min API**: 24 (Android 7.0)  
**Target API**: 34 (Android 14)  
**Build System**: Gradle (Kotlin DSL)  
**Language**: 100% Kotlin  
**Architecture Pattern**: MVVM + Fragment-based UI  

**Ready to explore? Start with QUICK_START.md!** ✨

