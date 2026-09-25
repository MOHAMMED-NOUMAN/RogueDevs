package com.itantra.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.prefs.EmergencyContact
import com.itantra.app.core.prefs.SpeechLanguage
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.app.core.prefs.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    val profile: StateFlow<UserProfile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile("", emptySet()))

    val speechLanguage: StateFlow<SpeechLanguage> = prefs.speechLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeechLanguage.ENGLISH)

    val emergencyContacts: StateFlow<List<EmergencyContact>> = prefs.emergencyContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveProfile(profile: UserProfile) {
        if (!profile.isComplete) return
        viewModelScope.launch { prefs.saveProfile(profile) }
    }

    fun setSpeechLanguage(language: SpeechLanguage) {
        viewModelScope.launch { prefs.setSpeechLanguage(language) }
    }

    /** Replaces the contact at [index], or adds [contact] when [index] is null. */
    fun saveContact(index: Int?, contact: EmergencyContact) {
        val current = emergencyContacts.value
        val updated = if (index == null) current + contact
        else current.mapIndexed { i, old -> if (i == index) contact else old }
        viewModelScope.launch { prefs.saveEmergencyContacts(updated) }
    }

    fun deleteContact(index: Int) {
        val updated = emergencyContacts.value.filterIndexed { i, _ -> i != index }
        viewModelScope.launch { prefs.saveEmergencyContacts(updated) }
    }
}
