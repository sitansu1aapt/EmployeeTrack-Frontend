package com.yatri

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "yatri_prefs")

object PrefKeys {
    val AUTH_TOKEN: Preferences.Key<String> = stringPreferencesKey("auth_token")
    val USER_JSON: Preferences.Key<String> = stringPreferencesKey("user_json")
    val ROLES_JSON: Preferences.Key<String> = stringPreferencesKey("roles_json")
    val IS_ON_DUTY: Preferences.Key<Boolean> = booleanPreferencesKey("is_on_duty")
    val ACTIVE_ROLE_ID: Preferences.Key<String> = stringPreferencesKey("active_role_id")
    val LANGUAGE: Preferences.Key<String> = stringPreferencesKey("language")
    val USER_NAME: Preferences.Key<String> = stringPreferencesKey("user_name")
    val ACTIVE_ROLE_NAME: Preferences.Key<String> = stringPreferencesKey("active_role_name")
}


