# Quick Start Guide - Running the App

## 🚀 Quick Start (5 Minutes)

### Step 1: Prerequisites Check
```bash
# Check if you have Android SDK installed
where adb

# Check Java version
java -version  # Should be Java 8 or higher
```

### Step 2: Build the App
**Option A - Android Studio:**
1. Open the project in Android Studio
2. Click the green "Run" button or press `Shift+F10`
3. Select your device/emulator
4. App will build and launch automatically

**Option B - Command Line:**
```bash
cd e:\AATPL\Yatri\EmployeeTrack-Frontend
./gradlew assembleDebug
```

**Output**: `app/build/outputs/apk/debug/app-universal-debug.apk`

### Step 3: Install & Run
```bash
# Install on device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# Launch app
adb shell am start -n com.yatri/.SplashActivity
```

### Step 4: View Logs
```bash
adb logcat
```

---

## 📋 App Walkthrough

### First Time Usage:
1. **Splash Screen** → 1.5 second loading screen
2. **Login Screen** → Choose login method (Employee ID/Email/Phone) + password
3. **Dashboard** → Check-in/Check-out, location tracking
4. **Bottom Navigation** → Switch between Dashboard, Tasks, Messages, Profile

### Test Credentials:
> Contact your administrator for test login credentials

---

## 🔧 Build Tasks Available

### In VS Code/Android Studio Terminal:

```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk

# View filtered logs
adb logcat -s DashboardFragment:D TasksFragment:D

# Clear logs
adb logcat -c
```

---

## 📱 Required Permissions (Grant When Prompted)

When you first launch the app:
- ✅ Allow Location Access (for tracking)
- ✅ Allow Camera (for selfies in check-in)
- ✅ Allow Notifications (for alerts)

---

## 🎯 Main Entry Points

| Screen | Purpose | Entry Point |
|--------|---------|-------------|
| SplashActivity | First screen on app load | LAUNCHER |
| LoginActivity | User authentication | After splash |
| EmployeeActivity | Main app interface | After login |
| DashboardFragment | Check-in/out, location | First tab |
| TasksFragment | View assigned tasks | Second tab |
| MessagesFragment | Notifications | Third tab |
| ProfileFragment | User settings | Fourth tab |

---

## 🐛 Common Issues & Fixes

### Issue: "android.os.FileUriExposedException" on Android 11+
**Fix**: Already handled in code - using FileProvider

### Issue: Location not working
**Fix**: Grant "Allow all the time" permission for location in app settings

### Issue: Cannot find gradlew
**Fix**: Ensure you're in the correct directory:
```bash
cd e:\AATPL\Yatri\EmployeeTrack-Frontend
```

### Issue: "Failed to install" on device
**Fix**: 
```bash
# Uninstall old version first
adb uninstall com.yatri

# Then install again
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

---

## 📊 App Statistics

- **Target API**: 34 (Android 14)
- **Minimum API**: 24 (Android 7.0)
- **Language**: Kotlin
- **Architecture**: Fragment-based with MVVM pattern
- **Build System**: Gradle (Kotlin DSL)
- **Version**: 1.2.6

---

## 🎯 Key Features Overview

✅ **Authentication** - Login with Employee ID/Email/Phone  
✅ **Attendance Tracking** - Check-in/Check-out with location  
✅ **Location Tracking** - Background GPS tracking with foreground service  
✅ **Task Management** - View, filter, sort, and update task status  
✅ **Notifications** - Firebase Cloud Messaging integration  
✅ **Multi-language Support** - English and Odia languages  
✅ **Sleep Alert System** - Drowsiness detection and alerts  
✅ **Maps Integration** - Google Maps display  
✅ **Patrol System** - Real-time patrol tracking  

---

## 📞 Getting Help

1. **Check Logcat**: `adb logcat` - Most issues show in logs
2. **Review Code**: Check `.kt` file mentioned in error
3. **Check Permissions**: Ensure all needed permissions granted
4. **Clear App Data**: Settings > Apps > EmployeeTrack > Storage > Clear Data

---

**Ready to run? Just click "Run" in Android Studio or execute `./gradlew assembleDebug`!**
