package com.itantra.app.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore by preferencesDataStore(name = "itantra_user")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usernameKey = stringPreferencesKey("username")
    private val sessionCodeKey = stringPreferencesKey("session_code")
    private val lastPeerIdKey = stringPreferencesKey("last_peer_id")
    private val lastPeerNameKey = stringPreferencesKey("last_peer_name")

    val username: Flow<String> = context.userDataStore.data.map { prefs ->
        prefs[usernameKey].orEmpty()
    }

    suspend fun setUsername(value: String) {
        context.userDataStore.edit { it[usernameKey] = value.trim() }
    }

    suspend fun usernameOrDefault(): String {
        val stored = username.first().trim()
        return stored.ifBlank { "Teammate" }
    }

    suspend fun sessionCode(): String {
        val existing = context.userDataStore.data.first()[sessionCodeKey]
        if (!existing.isNullOrBlank()) return existing
        val generated = "ITN-" + (1000..9999).random()
        context.userDataStore.edit { it[sessionCodeKey] = generated }
        return generated
    }

    fun deviceIdSuffix(): String =
        UUID.nameUUIDFromBytes(
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ).orEmpty().toByteArray()
        ).toString().take(4).uppercase()

    suspend fun saveLastPeer(id: String, name: String) {
        context.userDataStore.edit {
            it[lastPeerIdKey] = id
            it[lastPeerNameKey] = name
        }
    }

    suspend fun lastPeer(): Pair<String, String>? {
        val prefs = context.userDataStore.data.first()
        val id = prefs[lastPeerIdKey].orEmpty()
        val name = prefs[lastPeerNameKey].orEmpty()
        if (id.isBlank()) return null
        return id to name.ifBlank { "Teammate" }
    }
}
