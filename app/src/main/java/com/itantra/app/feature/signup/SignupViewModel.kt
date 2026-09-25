package com.itantra.app.feature.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.app.core.prefs.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    /** Saves the profile, then calls [onSaved]. Ignored unless the profile is complete. */
    fun save(profile: UserProfile, onSaved: () -> Unit) {
        if (!profile.isComplete) return
        viewModelScope.launch {
            prefs.saveProfile(profile)
            onSaved()
        }
    }
}
