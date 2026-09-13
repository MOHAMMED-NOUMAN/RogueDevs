package com.itantra.app.core.utils

/**
 * App-wide constants that don't belong to any single feature.
 * Keep this file small — a constant used by only one feature
 * belongs in that feature's package instead.
 */
object Constants {
    enum class SupportedLanguage(val displayName: String, val bcp47Tag: String) {
        HINDI("Hindi", "hi-IN"),
        GUJARATI("Gujarati", "gu-IN"),
        MARATHI("Marathi", "mr-IN"),
        KANNADA("Kannada", "kn-IN"),
        MALAYALAM("Malayalam", "ml-IN"),
        TAMIL("Tamil", "ta-IN"),
        TELUGU("Telugu", "te-IN"),
        ODIA("Odia", "or-IN"),
        BENGALI("Bengali", "bn-IN"),
        ENGLISH("English", "en-IN"),
    }
}
