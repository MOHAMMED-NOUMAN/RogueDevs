package com.itantra.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's entry point. Every @Inject / @HiltViewModel in the app
 * ultimately hangs off the dependency graph rooted here.
 */
@HiltAndroidApp
class ItantraApplication : Application()
