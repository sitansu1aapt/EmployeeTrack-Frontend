# Architecture Overview

## 📐 Application Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Android Application                            │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┘
                    ▼
          ┌─────────────────────┐
          │     MyApp.kt        │  ◄─── Application Entry Point
          │  (Custom App Class) │
          └─────────────────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
  ┌────────────────┐   ┌─────────────────┐
  │ AppContext     │   │ SleepAlert      │
  │ (Global Ctx)   │   │ Manager         │
  └────────────────┘   └─────────────────┘
         │
         ▼
   ┌──────────────────────────────────────────┐
   │         ACTIVITY FLOW                     │
   └──────────────────────────────────────────┘
         │
    ┌────▼─────────────────────────┐
    │   SplashActivity (1.5 sec)    │  LAUNCHER - First Screen
    │   • Loads auth token          │
    │   • Initializes localization  │
    └────┬──────────────────────────┘
         │
    ┌────┴──────────────────────────┐
    │    Check Token Valid?          │
    ├────────────────────────────────┤
    │ Token   │                      │
    │ Exists? │                      │
    └────┬────┘                      │
         │                           │
    ┌────▼──────┐            ┌───────▼──────┐
    │YES         │            │NO             │
    │   │        │            │   │           │
    │   ▼        │            │   ▼           │
    │ Employee   │            │ Login         │
    │ Activity   │            │ Activity      │
    │            │            │   • Multi-lang│
    │            │            │   • 3 login   │
    │            │            │     methods   │
    └────┬───────┘            └───────┬───────┘
         │                            │
         │             ┌──────────────┘
         │             │
         │             ▼
         │        ┌──────────────────────┐
         │        │ LoginActivity        │
         │        │ • Language SELECT    │
         │        │ • Identifier Type    │
         │        │ • Email/ID/Phone     │
         │        │ • Password           │
         │        │ • API: auth/login    │
         │        ├──────────────────────┤
         │        │ Saves token to:      │
         │        │ • TokenStore (RAM)   │
         │        │ • DataStore (Disk)   │
         │        └──────┬───────────────┘
         │               │
         └───────────────┤
                         │
                         ▼
          ┌──────────────────────────────┐
          │  EmployeeActivity            │  MAIN APP CONTAINER
          │  (Bottom Navigation Menu)    │
          ├──────────────────────────────┤
          │ Fragment Container           │
          │ (Displays selected fragment) │
          │                              │
          │ ┌────────────────────────┐   │
          │ │ Navigation Items:      │   │
          │ ├────────────────────────┤   │
          │ │ • Dashboard (Home)     │   │
          │ │ • Tasks                │   │
          │ │ • Messages             │   │
          │ │ • Profile              │   │
          │ └────────────────────────┘   │
          └──────────────────────────────┘
                         │
        ┌────────────────┼────────────────┬─────────────────┐
        │                │                │                 │
        ▼                ▼                ▼                 ▼
   ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
   │DASHBOARD   │  │TASKS       │  │MESSAGES    │  │PROFILE     │
   │Fragment    │  │Fragment    │  │Fragment    │  │Fragment    │
   ├────────────┤  ├────────────┤  ├────────────┤  ├────────────┤
   │• Check-in  │  │• Task List │  │• Notif     │  │• User Info │
   │• Check-out │  │• Filter    │  │  List      │  │• Logout    │
   │• Timer     │  │• Sort      │  │• Notif     │  │• Settings  │
   │• Maps      │  │• Status    │  │  Details   │  │• Language  │
   │• Location  │  │  Update    │  │• Firebase  │  └────────────┘
   │  Toggle    │  │• RecyclerV │  │  Push      │
   │• Duty      │  │  iew       │  │  Notif     │
   │  Status    │  └────────────┘  └────────────┘
   └────────────┘

```

---

## 🔄 Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     NETWORK LAYER                            │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Retrofit + OkHttp Client                           │   │
│  │  • Intercepts all requests                          │   │
│  │  • Adds Authorization: Bearer {token} header        │   │
│  │  • Handles network timeouts                         │   │
│  │  • Base URL: AppConfig.API_BASE_URL                │   │
│  └─────────────────────────────────────────────────────┘   │
│                         │                                    │
│                    ┌────▼────┐                               │
│                    │   APIs  │                               │
│                    └────┬────┘                               │
│                         │                                    │
│         ┌───────────────┼───────────────┐                   │
│         │               │               │                    │
│         ▼               ▼               ▼                    │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐              │
│  │AuthApi     │ │TasksApi    │ │ProfileApi  │              │
│  │ • login    │ │ • getTasks │ │ • getProf  │              │
│  │ • logout   │ │ • updateSt │ │ • updatePr │              │
│  │ • refresh  │ │ • deleteT  │ │ •  setting │              │
│  └────────────┘ └────────────┘ └────────────┘              │
│         │               │               │                    │
│         └───────────────┼───────────────┘                   │
│                         │                                    │
│                    ┌────▼────┐                               │
│                    │ Backend  │                               │
│                    │  API     │                               │
│                    │ Server   │                               │
│                    └──────────┘                               │
└─────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────┐
│                    LOCAL STORAGE LAYER                          │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  DataStore (Jetpack)                                     │  │
│  │  • Stores: Auth Token, Role ID, User ID, etc           │  │
│  │  • Location: /data/data/com.yatri/datastore/*.pb       │  │
│  │  • Thread-safe, Coroutine-based                        │  │
│  │  • Accessible via: context.dataStore.data.first()      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  TokenStore (In-Memory)                                 │  │
│  │  • Quick access: TokenStore.token                       │  │
│  │  • Loses value when app closes                          │  │
│  │  • Reloaded from DataStore on app start                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  SharedPreferences (Legacy)                             │  │
│  │  • Used for: Dashboard tracking state                   │  │
│  │  • Background tracking toggle, etc                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────┐
│                    SERVICES LAYER                               │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  LocationService                                         │  │
│  │  • Foreground Service (shows persistent notification)   │  │
│  │  • Runs even when app backgrounded                      │  │
│  │  • Fetches GPS coordinates periodically                │  │
│  │  • Sends to API backend                                 │  │
│  │  • Requires: android.permission.FOREGROUND_SERVICE     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MyMessagingService (FCM)                               │  │
│  │  • Receives push notifications                          │  │
│  │  • Action: com.google.firebase.MESSAGING_EVENT         │  │
│  │  • Routes to NotificationActivity                       │  │
│  │  • Updates UI based on notification type                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  SleepAlertManager                                       │  │
│  │  • Monitors for drowsiness/inactivity                   │  │
│  │  • Triggers SleepAlertActivity                          │  │
│  │  • Plays alert sound                                    │  │
│  │  • Shows quiz on screen                                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└────────────────────────────────────────────────────────────────┘

```

---

## 🧩 Component Relationships

```
┌──────────────────────────────────────────────────────────┐
│  UI Layer (Activities & Fragments)                       │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Fragment                                           │ │
│  │ • Use lifecycleScope for coroutines               │ │
│  │ • Observe LiveData/StateFlow                      │ │
│  │ • Update UI based on data changes                 │ │
│  │ • Communicate with APIs                           │ │
│  └────────────────────────────────────────────────────┘ │
│           │                                               │
│           ▼                                               │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ViewModel (if used)                                │ │
│  │ • Holds UI state                                   │ │
│  │ • Makes API calls                                  │ │
│  │ • Survives configuration changes                   │ │
│  │ • NOT FULLY IMPLEMENTED (mostly direct API calls)  │ │
│  └────────────────────────────────────────────────────┘ │
│           │                                               │
└───────────┼───────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────┐
│  Repository Layer (Network)                              │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Retrofit API Interfaces                            │ │
│  │ • AuthApi                                          │ │
│  │ • TasksApi                                         │ │
│  │ • ProfileApi                                       │ │
│  │ • Custom API interfaces                            │ │
│  └────────────────────────────────────────────────────┘ │
│           │                                               │
│           ▼                                               │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Network Configuration (Network.kt)                 │ │
│  │ • Retrofit instance                                │ │
│  │ • OkHttp client configuration                      │ │
│  │ • Request interceptors                             │ │
│  │ • Response handling                                │ │
│  └────────────────────────────────────────────────────┘ │
│           │                                               │
└───────────┼───────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────┐
│  Backend API                                              │
│  (REST endpoints at api.example.com)                      │
└──────────────────────────────────────────────────────────┘

```

---

## 🔐 Authentication & Token Flow

```
1. USER ENTERS CREDENTIALS
   ↓
2. LoginActivity calls: authApi.login(LoginRequest)
   ├─ identifierType: "empId" | "email" | "phone"
   ├─ identifier: user input
   └─ password: user input
   ↓
3. BACKEND VALIDATES
   ├─ Check if user exists
   ├─ Verify password
   └─ Generate JWT token
   ↓
4. RESPONSE: LoginResponse
   ├─ token: "eyJhbGc..." (JWT Token)
   ├─ userId: "123"
   ├─ roleId: "ROLE_EMPLOYEE"
   └─ userInfo: {...}
   ↓
5. STORE TOKEN
   ├─ Save to DataStore (persistent)
   │  └─ PrefKeys.AUTH_TOKEN = token
   └─ Save to TokenStore (in-memory)
      └─ TokenStore.token = token
   ↓
6. SUBSEQUENT API CALLS
   │
   └─ OkHttp Interceptor intercepts request
      ├─ Reads: TokenStore.token
      ├─ Adds header: "Authorization: Bearer {token}"
      └─ Sends request with header
   ↓
7. BACKEND VALIDATES TOKEN
   ├─ Extracts token from header
   ├─ Verifies signature
   ├─ Checks expiration
   └─ Allows/Denies request

```

---

## 📱 UI State Management

```
Fragment Lifecycle:
├─ onCreate()        → Initialize variables
├─ onCreateView()    → Inflate layout XML
├─ onViewCreated()   → Setup views & listeners
│  ├─ initializeViews()     → Find by ID
│  ├─ setupClickListeners() → Set onclick handlers
│  ├─ setupRecyclerView()   → Configure adapter
│  └─ loadData()            → Make API calls
│
├─ onStart()         → Resume background services
├─ onResume()        → Resume animation/listeners
├─ onPause()         → Pause animations
├─ onStop()          → Stop background services
├─ onDestroyView()   → Clean up views
└─ onDestroy()       → Clean up resources


Data Updates:
1. UI Action (click button)
   ↓
2. Call API: lifecycleScope.launch { api.call() }
   ↓
3. Receive Response
   ↓
4. Update ViewModel/State
   ↓
5. Update LiveData/StateFlow
   ↓
6. Fragment observes changes
   ↓
7. Update UI (RecyclerView, TextView, etc)

```

---

## 🎯 Dependencies Between Modules

```
MyApp.kt
  ├─ AppContext (Global context holder)
  └─ SleepAlertManager (Sleep alert initialization)

SplashActivity
  ├─ LocalizationManager (Language setup)
  ├─ DataStore (Load auth token)
  ├─ TokenStore (Set token in memory)
  └─ Routes to → LoginActivity or EmployeeActivity

LoginActivity
  ├─ LocalizationManager (Multi-language UI)
  ├─ AuthApi (Network)
  ├─ DataStore (Save token)
  ├─ TokenStore (Save token in memory)
  └─ Routes to → EmployeeActivity

EmployeeActivity (Main Container)
  ├─ DashboardFragment (Tab 1)
  ├─ TasksFragment (Tab 2)
  ├─ MessagesFragment (Tab 3)
  └─ ProfileFragment (Tab 4)

DashboardFragment
  ├─ LocationService (Start/stop)
  ├─ SleepAlertManager
  ├─ Maps API (Display map)
  ├─ Permissions manager
  └─ Check-in/Check-out API calls

TasksFragment
  ├─ TasksApi (Fetch tasks)
  ├─ TasksAdapter (RecyclerView)
  ├─ DataStore (Load roleId)
  └─ Filter/Sort logic

MessagesFragment
  ├─ FCM Service (Receives pushes)
  ├─ Notifications API
  └─ NotificationAdapter

ProfileFragment
  ├─ ProfileApi (Get user info)
  ├─ LocalizationManager (Change language)
  ├─ DataStore (Save preferences)
  └─ Logout functionality

```

---

## 🔌 Permission Request Flow

```
AndroidManifest.xml declares permissions
         ↓
App installs on device
         ↓
Runtime Permissions Needed (Android 6.0+)
         ↓
SplashActivity.ensureNotificationsEnabled()
├─ Check if POST_NOTIFICATIONS granted
├─ Request if not granted
└─ Open notification settings if disabled
         ↓
DashboardFragment.ensureLocationPermission()
├─ Check if FINE_LOCATION/COARSE_LOCATION granted
└─ Request if not granted
         ↓
LocationService starts
├─ Uses FOREGROUND_SERVICE permission
└─ Shows persistent notification
         ↓
User activities proceed

```

---

**This architecture uses:**
- Fragment-based UI (Modern Android)
- Coroutines for async operations
- Retrofit for networking
- DataStore for persistence
- Services for background operations
- LiveData/Flow for reactive updates

