# Petuk Maharaj Restaurant App Customization

## Overview
This document outlines the customization changes made to rebrand the app for "Petuk Maharaj" - a restaurant in Kolkata.

## Changes Made

### 1. App Name
**File:** `app/src/main/res/values/strings.xml`
- Changed app name from "Flying Chital" to **"Petuk Maharaj"**

### 2. Color Scheme
**File:** `app/src/main/res/values/colors.xml`

#### Primary Colors (Restaurant Theme)
- **Primary Color:** `#C41E3A` - Deep maroon/crimson (evokes traditional Bengali restaurant elegance)
- **Primary Dark:** `#8B1528` - Darker maroon for depth
- **Accent Color:** `#D4AF37` - Golden (represents richness and quality)
- **Accent Light:** `#F4E4C1` - Light golden cream
- **Background:** `#FFF8F0` - Warm off-white (welcoming and clean)
- **Card Background:** `#FFF5E8` - Warm cream

#### Additional Restaurant Colors
- **Terracotta:** `#E07A5F` - Earthy warmth
- **Warm Brown:** `#8B4513` - Traditional wood tones
- **Restaurant Cream:** `#FFFAEB` - Soft, inviting
- **Spice:** `#D2691E` - Warm spice tones

### 3. Dark Mode Colors
**File:** `app/src/main/res/values-night/colors.xml`
- **Primary (Dark):** `#D4394C` - Lighter maroon for visibility
- **Primary Dark:** `#A02030` - Deep maroon
- **Background:** `#1A0F0F` - Warm dark brown (not pure black)
- **Card Background:** `#2A1A1A` - Warm dark cards
- **Border:** `#4A3030` - Warm dark borders

### 4. Theme Enhancements
**File:** `app/src/main/res/values/themes.xml`

Added comprehensive Material Design theme with:
- Custom primary and accent colors
- Status bar styling (maroon)
- Custom button styles with rounded corners
- Card view styling with elevation
- Typography improvements

#### Custom Styles Added:
1. **Widget.App.Button** - Primary button style
2. **Widget.App.Button.Outlined** - Outlined button variant
3. **Widget.App.CardView** - Card styling with rounded corners

## Color Psychology for Restaurant Branding

### Why These Colors?
1. **Maroon/Crimson (#C41E3A):**
   - Evokes appetite and warmth
   - Traditional and elegant
   - Common in Bengali/Kolkata restaurant branding
   - Creates sense of heritage and authenticity

2. **Golden Accent (#D4AF37):**
   - Represents quality and premium service
   - Associated with richness and celebration
   - Complements the maroon beautifully
   - Traditional in Indian restaurant design

3. **Warm Backgrounds (#FFF8F0):**
   - Creates welcoming atmosphere
   - Easy on eyes for extended use
   - Evokes cleanliness and freshness
   - Better than stark white for food apps

4. **Terracotta & Earth Tones:**
   - Connect to traditional Bengali clay pottery
   - Warm and inviting
   - Cultural authenticity
   - Natural and organic feel

## Visual Impact

The color scheme creates:
- **Warmth:** Inviting customers in
- **Heritage:** Traditional Bengali restaurant feel
- **Quality:** Premium dining experience
- **Appetite:** Colors that stimulate hunger
- **Trust:** Established, reliable brand

## Next Steps (Optional Enhancements)

1. **App Icon:** Create a custom icon with maroon and gold colors
2. **Splash Screen:** Design with restaurant branding
3. **Typography:** Consider Bengali-friendly fonts
4. **Images:** Add food photography with warm tones
5. **Illustrations:** Custom illustrations of Bengali cuisine

## Technical Notes

- All colors follow Material Design guidelines
- Dark mode maintains brand consistency
- Accessibility contrast ratios maintained
- Theme is fully compatible with Material Components
- No breaking changes to existing layouts
