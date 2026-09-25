package com.itantra.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.prefs.UserPreferences
import com.itantra.app.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Picks the first screen: Home once signup is done, Signup otherwise. */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    prefs: UserPreferences,
) : ViewModel() {

    /** Null until the saved profile has been read; the splash screen stays up until then. */
    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val done = prefs.profile.first().isComplete
            _startRoute.value = if (done) Screen.Home.route else Screen.Signup.route
        }
    }
}
