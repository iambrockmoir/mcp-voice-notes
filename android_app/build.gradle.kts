// Top-level build file for Voice Notes Android App
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Project-wide build configuration
buildscript {
    extra.apply {
        set("compose_version", "1.5.4")
        set("supabase_version", "2.0.4")
        set("retrofit_version", "2.9.0")
        set("room_version", "2.5.0")
        set("work_version", "2.8.1")
        set("hilt_version", "2.48")
    }
}