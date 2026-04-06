# UI & Layout Guide

## 📱 Complete Screen Layouts

### Screen 1: SplashActivity

```
┌─────────────────────────────────┐
│                                 │
│                                 │
│          SPLASH LOGO            │
│                                 │
│                                 │
│                                 │
│         Loading... (1.5 sec)    │
│                                 │
│                                 │
└─────────────────────────────────┘

File: activity_splash.xml
Duration: 1.5 seconds
Next Screen: LoginActivity or EmployeeActivity (depends on token)
```

---

### Screen 2: LoginActivity

```
┌─────────────────────────────────┐
│  ←  Sign In                     │  (Header)
├─────────────────────────────────┤
│                                 │
│  🌐 Select Language             │
│  ├─ English                     │  (Dropdown)
│  └─ Odia                        │
│                                 │
│  Welcome Back                   │
│                                 │
│  Sign in to continue            │
│                                 │
│  Login Type: ▼                  │
│  ├─ Employee ID (Selected)      │  (Spinner)
│  ├─ Email                       │
│  └─ Phone Number                │
│                                 │
│  Employee ID:  [ _________ ]    │  (EditText)
│                                 │
│  Password:     [ _________ ]    │  (EditText, Password Mode)
│                                 │
│  ☑ Remember me                  │  (CheckBox)
│                                 │
│  ┌─────────────────────────────┐│
│  │    LOGIN BUTTON             ││  (Full Width Button)
│  └─────────────────────────────┘│
│                                 │
│  Forgot Password? ← Link        │  (TextView Link)
│                                 │
└─────────────────────────────────┘

File: activity_login.xml
Components:
  - Language AutoCompleteTextView
  - Login Type Spinner
  - Identifier AutocompleteTextView
  - Password TextInputEditText
  - Remember Me CheckBox
  - Login Button
  - Forgot Password Link

Navigation:
  - Click Login → Call authApi.login()
  - Success → EmployeeActivity
  - Failure → Show Toast error
```

---

### Screen 3: EmployeeActivity (Main App)

```
┌─────────────────────────────────┐
│  Yatri                          │  (App Title)
├─────────────────────────────────┤
│                                 │
│  [Fragment Container]           │
│  (Shows DashboardFragment,      │
│   TasksFragment, etc based on   │
│   selected tab)                 │
│                                 │
│                                 │
│                                 │
│                                 │
│                                 │
│                                 │
├─────────────────────────────────┤
│ 🏠    ✅     💬     👤         │  (Bottom Navigation)
│Dash   Tasks  Msgs   Profile     │
└─────────────────────────────────┘

File: activity_employee.xml
Structure:
  - FrameLayout: Fragment container at top
  - BottomNavigationView: Navigation menu

Navigation Tabs:
  1. Dashboard (R.id.tab_dashboard) → DashboardFragment.kt
  2. Tasks (R.id.tab_tasks) → TasksFragment.kt
  3. Messages (R.id.tab_messages) → MessagesFragment.kt
  4. Profile (R.id.tab_profile) → ProfileFragment.kt
```

---

### Screen 4: DashboardFragment (Check-in/Location)

```
┌─────────────────────────────────┐
│ Dashboard                       │  (Title)
├─────────────────────────────────┤
│                                 │
│ Current Status:                 │
│ ┌─────────────────────────────┐ │
│ │  OFF DUTY  ⏱️ 00:00:00    │ │  (Status Card)
│ └─────────────────────────────┘ │
│                                 │
│ Check-In / Check-Out:           │
│ ┌──────────────┬──────────────┐ │
│ │ [CHECK IN]   │ [CHECK OUT]  │ │  (Two Buttons)
│ └──────────────┴──────────────┘ │
│                                 │
│ Background Tracking:            │
│ 📍 Toggle: [ON/OFF Switch]      │  (Switch Button)
│ Status: Tracking INACTIVE       │
│                                 │
│ Map Display:                    │
│ ┌─────────────────────────────┐ │
│ │                             │ │
│ │  [Google Map Fragment]      │ │  (SupportMapFragment)
│ │  Shows current location     │ │
│ │                             │ │
│ └─────────────────────────────┘ │
│                                 │
│ Duty History:                   │
│ ┌─────────────────────────────┐ │
│ │ In: 09:00 AM (2024-01-15)   │ │  (RecyclerView/List)
│ │ Out: Pending                │ │
│ └─────────────────────────────┘ │
│                                 │
└─────────────────────────────────┘

File: fragment_dashboard.xml
Key Components:
  - TextView: Duty status
  - TextView: Duty timer
  - Button: Check-In
  - Button: Check-Out
  - Switch: Background tracking toggle
  - TextView: Tracking status
  - SupportMapFragment: Map display
  - RecyclerView: Duty history
  - SwipeRefreshLayout: Pull to refresh

Events:
  - Check-In Button → checkIn() function
  - Check-Out Button → checkOut() function
  - Toggle Switch → toggleBackgroundTracking() function
  - Timer updates every 1 second when ON DUTY
```

---

### Screen 5: TasksFragment (Task List)

```
┌─────────────────────────────────┐
│ Tasks                           │  (Title)
├─────────────────────────────────┤
│ Filter: [All ▼]  Sort: [By Date ▼]  (Spinners)
├─────────────────────────────────┤
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Task 1 (PENDING)            │ │
│ │ Backend API Testing         │ │  (Task Card 1)
│ │ Priority: High              │ │
│ │ [Update To In Progress]     │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Task 2 (IN_PROGRESS)        │ │
│ │ UI Testing                  │ │  (Task Card 2)
│ │ Priority: Medium            │ │
│ │ [Update To Completed]       │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Task 3 (COMPLETED) ✓        │ │
│ │ Documentation               │ │  (Task Card 3)
│ │ Priority: Low               │ │
│ │ [Already Completed]         │ │
│ └─────────────────────────────┘ │
│                                 │
│    (Pull to refresh)            │  (SwipeRefresh)
│                                 │
└─────────────────────────────────┘

File: fragment_tasks.xml
Key Components:
  - Spinner: Filter tasks by status
  - Spinner: Sort tasks
  - ProgressBar: Loading indicator
  - RecyclerView: Task list display
  - SwipeRefreshLayout: Pull to refresh
  - TextView: Empty state message

File: item_task.xml (Individual Task Card)
  - TextView: Task title
  - TextView: Description
  - TextView: Priority level
  - TextView: Current status
  - Button: Update status button

Events:
  - Filter Spinner → applyFiltersAndSort()
  - Sort Spinner → applyFiltersAndSort()
  - Update Button → handleStatusUpdate(task)
  - Swipe to Refresh → loadTasks()
```

---

### Screen 6: MessagesFragment (Notifications)

```
┌─────────────────────────────────┐
│ Messages                        │  (Title)
├─────────────────────────────────┤
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 09:30 AM  Task Assigned     │ │
│ │ "New task: API Development" │ │  (Notification 1)
│ │ [Unread Badge] •            │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 08:15 AM  Deadline Alert    │ │
│ │ "Task due in 2 hours"       │ │  (Notification 2)
│ │ [Read] ✓                    │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Yesterday  Reminder         │ │
│ │ "Submit your timesheet"     │ │  (Notification 3)
│ │ [Read] ✓                    │ │
│ └─────────────────────────────┘ │
│                                 │
│ No more messages                │  (or Empty state)
│                                 │
└─────────────────────────────────┘

File: fragment_messages.xml
Key Components:
  - RecyclerView: Notifications list
  - ProgressBar: Loading
  - TextView: Empty state message

File: item_notification.xml
  - TextView: Timestamp
  - TextView: Title
  - TextView: Message body
  - Badge/Indicator: Read/Unread status

Events:
  - Click Notification → startActivity(NotificationDetailActivity)
  - Swipe → Delete notification
```

---

### Screen 7: ProfileFragment (User Profile)

```
┌─────────────────────────────────┐
│ Profile                         │  (Title)
├─────────────────────────────────┤
│                                 │
│          👤                     │  (Profile Avatar)
│     John Doe                    │  (Name)
│   Employee ID: EMP001           │  (ID)
│                                 │
├─────────────────────────────────┤
│ Information                     │  (Section Header)
├─────────────────────────────────┤
│ Email: john@company.com         │
│ Phone: +91 9876543210          │
│ Department: Engineering        │
│ Role: Senior Developer         │
│ Joining Date: 01 Jan 2022      │
│                                 │
├─────────────────────────────────┤
│ Settings                        │  (Section Header)
├─────────────────────────────────┤
│ Language:  [English ▼]          │  (Spinner)
│            Change to Odia      │
│                                 │
│ Notifications: [ON] Toggle      │  (Switch)
│                                 │
│ Dark Mode: [OFF] Toggle         │  (Switch)
│                                 │
├─────────────────────────────────┤
│ Actions                         │
├─────────────────────────────────┤
│ ┌─────────────────────────────┐ │
│ │  [CHANGE PASSWORD]          │ │  (Button)
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │  [EDIT PROFILE]             │ │  (Button)
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │  [LOGOUT] ⏻                 │ │  (Red Button)
│ └─────────────────────────────┘ │
│                                 │
└─────────────────────────────────┘

File: fragment_profile.xml
Key Components:
  - ImageView: Profile avatar
  - TextView: User name
  - TextView: User ID
  - RecyclerView or LinearLayout: User info sections
  - Spinner: Language selector
  - Switch: Notifications toggle
  - Switch: Dark mode toggle
  - Button: Change password
  - Button: Edit profile
  - Button: Logout

Events:
  - Language Spinner → recreate() [Refresh UI in new language]
  - Logout Button → logout() → Clear token → LoginActivity
```

---

### Special Screen: NotificationDetailActivity

```
┌─────────────────────────────────┐
│ ← Task Assigned                 │  (Back Button + Title)
├─────────────────────────────────┤
│                                 │
│ Timestamp:                      │
│ 09:30 AM, January 15, 2024      │
│                                 │
│ Title:                          │
│ Backend API Development         │
│                                 │
│ Message:                        │
│ You have been assigned a new    │
│ task to develop the user        │
│ authentication API endpoint.    │
│ Priority: HIGH                  │
│                                 │
│ Further Actions:                │
│ ┌──────────────┬──────────────┐ │
│ │  [VIEW TASK] │ [DISMISS]    │ │
│ └──────────────┴──────────────┘ │
│                                 │
└─────────────────────────────────┘

File: activity_notification_detail.xml
Components:
  - Toolbar with back button
  - TextView: Timestamp
  - TextView: Title
  - TextView: Message body
  - TextView: Additional details
  - Buttons: Actions
```

---

## 📐 Layout Hierarchy & XML Files

```
res/layout/
├── activity_splash.xml
│   ├── ImageView (logo)
│   └── ProgressBar
│
├── activity_login.xml
│   ├── LinearLayout (vertical)
│   │   ├── MaterialAutoCompleteTextView (language)
│   │   ├── MaterialAutoCompleteTextView (login type)
│   │   ├── TextInputEditText (identifier)
│   │   ├── TextInputEditText (password)
│   │   ├── CheckBox (remember me)
│   │   ├── Button (login)
│   │   └── TextView (forgot password)
│   
├── activity_employee.xml
│   ├── FrameLayout (fragment container)
│   └── BottomNavigationView
│
├── fragment_dashboard.xml
│   ├── SwipeRefreshLayout
│   │   └── LinearLayout (vertical)
│   │       ├── Card: Status display
│   │       ├── LinearLayout: Buttons
│   │       ├── Switch: Tracking toggle
│   │       ├── SupportMapFragment
│   │       └── RecyclerView: Duty history
│
├── fragment_tasks.xml
│   ├── LinearLayout
│   │   ├── LinearLayout: Filters & Sort (spinners)
│   │   ├── ProgressBar (loading)
│   │   ├── SwipeRefreshLayout
│   │   │   ├── RecyclerView (tasks)
│   │   │   └── TextView (empty state)
│
├── fragment_messages.xml
│   ├── RecyclerView (notifications)
│   └── TextView (empty state)
│
├── fragment_profile.xml
│   ├── LinearLayout (vertical)
│   │   ├── ImageView (avatar)
│   │   ├── TextView (name)
│   │   ├── Section: Info
│   │   ├── Section: Settings
│   │   └── Section: Actions (buttons)
│
├── item_task.xml
│   └── CardView
│       ├── TextView (title)
│       ├── TextView (description)
│       ├── TextView (priority)
│       ├── TextView (status)
│       └── Button (update)
│
├── item_notification.xml
│   └── LinearLayout
│       ├── TextView (timestamp)
│       ├── TextView (title)
│       ├── TextView (body)
│       └── Badge (unread indicator)
│
└── activity_notification_detail.xml
    ├── Toolbar
    └── ScrollView
        └── LinearLayout
            ├── TextView (timestamp)
            ├── TextView (title)
            ├── TextView (message)
            └── LinearLayout (buttons)
```

---

## 🎨 Color & Style Resources

File: `res/values/colors.xml`
```xml
<color name="primary">#2196F3</color>           <!-- Blue -->
<color name="primary_dark">#1976D2</color>     <!-- Dark Blue -->
<color name="accent">#FF4081</color>            <!-- Pink -->
<color name="background_white">#FFFFFF</color>
<color name="text_primary">#000000</color>
<color name="text_secondary">#757575</color>
<color name="status_pending">#FF9800</color>   <!-- Orange -->
<color name="status_progress">#4CAF50</color>  <!-- Green -->
<color name="status_completed">#66BB6A</color> <!-- Light Green -->
<color name="error_red">#F44336</color>        <!-- Red -->
```

File: `res/values/strings.xml`
```xml
<string name="app_name">EmployeeTrack</string>
<string name="welcome_back">Welcome Back</string>
<string name="sign_in_to_continue">Sign in to continue</string>
<string name="enter_employee_id">Enter Employee ID</string>
<string name="enter_password">Enter Password</string>
<string name="login">Login</string>
<string name="logout">Logout</string>
<string name="english">English</string>
<string name="odia">Odia</string>
<!-- ... more strings -->
```

---

## 📊 Dimensions & Spacing

File: `res/values/dimens.xml`
```xml
<dimen name="default_padding">16dp</dimen>
<dimen name="default_margin">16dp</dimen>
<dimen name="button_height">48dp</dimen>
<dimen name="button_corner_radius">8dp</dimen>
<dimen name="card_corner_radius">12dp</dimen>
<dimen name="default_text_size">16sp</dimen>
<dimen name="title_text_size">20sp</dimen>
<dimen name="small_text_size">12sp</dimen>
```

---

## 🎯 UI Interaction Flow

```
User Interaction Flow:
1. Tap Button
   ↓
2. onClickListener triggered
   ↓
3. Function called (e.g., checkIn())
   ↓
4. Check permissions
   ↓
5. Get user input / data
   ↓
6. Show loading indicator
   ↓
7. Make API call (lifecycleScope.launch)
   ↓
8. Backend processes request
   ↓
9. Receive response
   ↓
10. Hide loading indicator
   ↓
11. Update UI (setText, update adapter, etc)
   ↓
12. Show success/error Toast
   ↓
13. Navigation (if needed)
```

---

## ✨ UI Animation & Transitions

```
Fragment Transitions:
DashboardFragment
       ↓ (user clicks Tasks tab)
       ↓
FragmentTransaction.replace()
(old fragment removed, new fragment added)
       ↓
TasksFragment
       ↓ (user clicks Messages tab)
       ↓
MessagesFragment

Smooth slide animation (RecyclerView item changes)
```

---

This guide covers all major UI screens and their layouts. Each layout is designed to be user-friendly and follows Material Design principles.

