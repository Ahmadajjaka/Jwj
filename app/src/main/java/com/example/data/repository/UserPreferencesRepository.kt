package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.mamoDataStore: DataStore<Preferences> by preferencesDataStore(name = "mamo_prefs")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        // Default is Light Mode (false) per user selection
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val ACTIVE_STAFF_ID = longPreferencesKey("active_staff_id")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        val SAVED_IDENTIFIER = stringPreferencesKey("saved_login_identifier")
        val ACCOUNT_IDENTIFIERS_JSON = stringPreferencesKey("account_identifiers_json")
        val IS_GUEST_SESSION = booleanPreferencesKey("is_guest_session")
    }

    val isDarkTheme: Flow<Boolean> = context.mamoDataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_THEME] ?: false
    }

    val activeStaffId: Flow<Long?> = context.mamoDataStore.data.map { prefs ->
        val id = prefs[Keys.ACTIVE_STAFF_ID] ?: 0L
        if (id > 0L) id else null
    }

    val rememberMe: Flow<Boolean> = context.mamoDataStore.data.map { prefs ->
        prefs[Keys.REMEMBER_ME] ?: false
    }

    val savedLoginIdentifier: Flow<String> = context.mamoDataStore.data.map { prefs ->
        prefs[Keys.SAVED_IDENTIFIER] ?: ""
    }

    val isGuestSession: Flow<Boolean> = context.mamoDataStore.data.map { prefs ->
        prefs[Keys.IS_GUEST_SESSION] ?: false
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.mamoDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = enabled
        }
    }

    suspend fun setActiveStaffId(staffId: Long?) {
        context.mamoDataStore.edit { prefs ->
            if (staffId == null) {
                prefs.remove(Keys.ACTIVE_STAFF_ID)
            } else {
                prefs[Keys.ACTIVE_STAFF_ID] = staffId
                prefs[Keys.IS_GUEST_SESSION] = false
            }
        }
    }

    suspend fun setGuestSession(enabled: Boolean) {
        context.mamoDataStore.edit { prefs ->
            prefs[Keys.IS_GUEST_SESSION] = enabled
            if (enabled) {
                prefs.remove(Keys.ACTIVE_STAFF_ID)
            }
        }
    }

    suspend fun setRememberMe(enabled: Boolean, identifier: String) {
        context.mamoDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_ME] = enabled
            if (enabled && identifier.isNotBlank()) {
                prefs[Keys.SAVED_IDENTIFIER] = identifier.trim()
            } else {
                prefs.remove(Keys.SAVED_IDENTIFIER)
            }
        }
    }

    suspend fun bindAccountIdentifier(identifier: String, staffId: Long) {
        val normalized = identifier.trim().lowercase()
        if (normalized.isEmpty() || staffId <= 0L) return
        context.mamoDataStore.edit { prefs ->
            val current = runCatching {
                JSONObject(prefs[Keys.ACCOUNT_IDENTIFIERS_JSON] ?: "{}")
            }.getOrElse { JSONObject() }
            current.put(normalized, staffId)
            prefs[Keys.ACCOUNT_IDENTIFIERS_JSON] = current.toString()
        }
    }

    suspend fun findStaffIdByIdentifier(identifier: String): Long? {
        val normalized = identifier.trim().lowercase()
        if (normalized.isEmpty()) return null
        val raw = context.mamoDataStore.data.first()[Keys.ACCOUNT_IDENTIFIERS_JSON] ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            if (obj.has(normalized)) obj.getLong(normalized) else null
        }.getOrNull()
    }
}
