package com.yatri.localization

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import java.util.*

object LocalizationManager {
    private const val PREF_LANGUAGE = "selected_language"
    private const val PREF_NAME = "app_preferences"
    private var currentLanguage = "en"
    
    fun setLanguage(context: Context, language: String) {
        Log.d("LocalizationManager", "Setting language to: $language")
        currentLanguage = language
        
        // Save to preferences
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
        
        // Update locale
        updateLocale(context, language)
    }
    
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, "en") ?: "en"
    }
    
    fun getCurrentLanguage(): String = currentLanguage
    
    fun toggleLanguage(context: Context) {
        val newLanguage = if (currentLanguage == "en") "or" else "en"
        setLanguage(context, newLanguage)
    }
    
    private fun updateLocale(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        val config = Configuration()
        config.locale = locale
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
    
    fun getLocalizedString(context: Context, key: String): String {
        val language = getLanguage(context)
        return getStringByLanguage(context, key, language)
    }
    
    private fun getStringByLanguage(context: Context, key: String, language: String): String {
        return try {
            val resources = context.resources
            val packageName = context.packageName
            
            // Get string resource ID
            val resId = resources.getIdentifier(key, "string", packageName)
            if (resId != 0) {
                resources.getString(resId)
            } else {
                // Fallback to default English
                val config = Configuration(resources.configuration)
                config.locale = Locale("en")
                val englishResources = context.createConfigurationContext(config).resources
                englishResources.getString(resId)
            }
        } catch (e: Exception) {
            Log.e("LocalizationManager", "Error getting localized string for key: $key", e)
            key // Return key as fallback
        }
    }
    
    fun initialize(context: Context) {
        currentLanguage = getLanguage(context)
        updateLocale(context, currentLanguage)
    }
}

