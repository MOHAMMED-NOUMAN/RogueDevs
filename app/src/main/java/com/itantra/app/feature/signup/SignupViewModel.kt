package com.itantra.app.feature.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {
    fun saveUsername(name: String) {
        viewModelScope.launch { prefs.setUsername(name) }
    }
}
