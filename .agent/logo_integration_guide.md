# Petuk Maharaj Logo Integration Guide

## Logo File Location
**Path:** `app/src/main/res/drawable/petuk_logo.png`

## Logo Integration Summary

### ✅ Implemented

#### 1. **Login Screen** (`activity_login.xml`)
- **Location:** Header section
- **Size:** 80dp x 80dp
- **Position:** Left side of header, next to "Petuk Maharaj" text
- **Background:** Maroon primary color (#C41E3A)
- **Purpose:** Brand recognition on first user interaction

#### 2. **Splash Screen** (`activity_splash.xml`)
- **Location:** Center of screen
- **Size:** 180dp x 180dp
- **Layout:** Vertical center with restaurant name and tagline
- **Background:** Warm cream (#FFF8F0)
- **Elements:**
  - Logo (180dp)
  - "Petuk Maharaj" text (32sp, bold, serif font)
  - "Authentic Kolkata Cuisine" tagline (16sp, serif font)
- **Purpose:** First impression and brand establishment

## Design Specifications

### Logo Display Guidelines

#### Size Recommendations:
- **Small:** 40dp x 40dp (for toolbar/navigation)
- **Medium:** 80dp x 80dp (for headers/cards)
- **Large:** 180dp x 180dp (for splash/welcome screens)
- **Extra Large:** 240dp x 240dp (for promotional screens)

#### Color Backgrounds:
- **Primary Background:** Use on maroon (#C41E3A) - ensure logo has good contrast
- **Light Background:** Use on cream (#FFF8F0) or white
- **Dark Background:** Use on dark mode backgrounds (#1A0F0F)

#### Spacing:
- **Minimum padding:** 16dp around logo
- **Recommended margin:** 24dp for breathing room
- **With text:** 16dp spacing between logo and text

## Additional Integration Opportunities

### Recommended (Not Yet Implemented):

#### 1. **App Icon/Launcher**
- Replace current launcher icon with Petuk Maharaj logo
- Create adaptive icon versions
- **Files to update:**
  - `mipmap-hdpi/ic_launcher.png`
  - `mipmap-mdpi/ic_launcher.png`
  - `mipmap-xhdpi/ic_launcher.png`
  - `mipmap-xxhdpi/ic_launcher.png`
  - `mipmap-xxxhdpi/ic_launcher.png`

#### 2. **Employee Dashboard** (`activity_employee.xml`)
- Add small logo (40dp) to toolbar/header
- Position: Top-left corner
- Purpose: Consistent branding across all screens

#### 3. **Navigation Drawer/Menu**
- Add logo to drawer header
- Size: 120dp x 120dp
- Background: Primary or gradient

#### 4. **About/Settings Screen**
- Display logo with app version info
- Size: 100dp x 100dp
- Include restaurant information

#### 5. **Notification Icon**
- Use simplified version of logo for notification icon
- Create monochrome version for status bar
- **File:** `drawable/ic_notification.xml`

#### 6. **Empty States**
- Use logo with opacity for empty list states
- Size: 120dp x 120dp
- Opacity: 0.3-0.5

## Technical Implementation

### XML Reference:
```xml
<ImageView
    android:layout_width="80dp"
    android:layout_height="80dp"
    android:src="@drawable/petuk_logo"
    android:contentDescription="Petuk Maharaj Logo"
    android:scaleType="fitCenter"/>
```

### Programmatic Reference (Kotlin):
```kotlin
imageView.setImageResource(R.drawable.petuk_logo)
imageView.contentDescription = "Petuk Maharaj Logo"
imageView.scaleType = ImageView.ScaleType.FIT_CENTER
```

## Logo Optimization

### Current File:
- **Size:** 1,144,895 bytes (~1.1 MB)
- **Format:** PNG

### Recommendations:
1. **Optimize file size** - Consider reducing to 200-300 KB
2. **Create multiple densities:**
   - `drawable-mdpi/` (48dp baseline)
   - `drawable-hdpi/` (72dp)
   - `drawable-xhdpi/` (96dp)
   - `drawable-xxhdpi/` (144dp)
   - `drawable-xxxhdpi/` (192dp)
3. **Vector alternative** - Convert to SVG/Vector drawable for scalability
4. **WebP format** - Consider WebP for better compression

## Branding Consistency

### Logo Usage Rules:
1. ✅ Always maintain aspect ratio
2. ✅ Use `fitCenter` or `centerInside` for scaleType
3. ✅ Provide meaningful contentDescription for accessibility
4. ✅ Ensure sufficient contrast with background
5. ❌ Don't stretch or distort the logo
6. ❌ Don't use on busy backgrounds without proper spacing
7. ❌ Don't overlay text directly on logo

## Color Combinations

### Recommended Pairings:
1. **Logo + Maroon Background** (#C41E3A)
   - Use for headers and primary sections
   
2. **Logo + Cream Background** (#FFF8F0)
   - Use for main content areas
   
3. **Logo + White Background** (#FFFFFF)
   - Use for cards and clean sections
   
4. **Logo + Golden Accent** (#D4AF37)
   - Use sparingly for special promotions

## Accessibility

### Content Descriptions:
- **English:** "Petuk Maharaj Logo"
- **Context-specific:** "Petuk Maharaj Restaurant Logo"

### Contrast Requirements:
- Ensure logo meets WCAG 2.1 contrast ratios
- Test on all background colors
- Provide alternative versions if needed

## Files Modified

1. ✅ `app/src/main/res/layout/activity_login.xml`
2. ✅ `app/src/main/res/layout/activity_splash.xml`

## Next Steps

To complete the logo integration:
1. Optimize logo file size
2. Create density-specific versions
3. Update app launcher icon
4. Add to employee dashboard
5. Implement in navigation drawer
6. Create notification icon variant
