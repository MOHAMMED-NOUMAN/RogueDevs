package com.itantra.app.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Name and preferred languages entered at signup. */
data class UserProfile(
    val username: String,
    val languages: Set<String>,
) {
    /** Signup is finished only with both a name and at least one language. */
    val isComplete: Boolean get() = username.isNotBlank() && languages.isNotEmpty()
}

/** Languages the offline speech models cover today. */
enum class SpeechLanguage(val label: String) {
    ENGLISH("English"),
    HINDI("Hindi"),
}

@Serializable
data class EmergencyContact(
    val name: String,
    val number: String,
)

/** Everything the user sets up in the app, kept across restarts. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            username = prefs[USERNAME].orEmpty(),
            languages = prefs[LANGUAGES].orEmpty(),
        )
    }

    /** The language Hold to Talk listens for. */
    val speechLanguage: Flow<SpeechLanguage> = context.dataStore.data.map { prefs ->
        prefs[SPEECH_LANGUAGE]?.let { saved -> SpeechLanguage.entries.firstOrNull { it.name == saved } }
            ?: SpeechLanguage.ENGLISH
    }

    val emergencyContacts: Flow<List<EmergencyContact>> = context.dataStore.data.map { prefs ->
        prefs[EMERGENCY_CONTACTS]?.let { saved ->
            runCatching { json.decodeFromString<List<EmergencyContact>>(saved) }.getOrNull()
        } ?: DEFAULT_CONTACTS
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = profile.username.trim()
            prefs[LANGUAGES] = profile.languages
        }
    }

    suspend fun setSpeechLanguage(language: SpeechLanguage) {
        context.dataStore.edit { it[SPEECH_LANGUAGE] = language.name }
    }

    suspend fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        context.dataStore.edit { it[EMERGENCY_CONTACTS] = json.encodeToString(contacts) }
    }

    private companion object {
        val USERNAME = stringPreferencesKey("username")
        val LANGUAGES = stringSetPreferencesKey("languages")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val EMERGENCY_CONTACTS = stringPreferencesKey("emergency_contacts")

        /** Shown until the user edits the list: India's single emergency number. */
        val DEFAULT_CONTACTS = listOf(EmergencyContact("Local Emergency", "112"))
    }
}
