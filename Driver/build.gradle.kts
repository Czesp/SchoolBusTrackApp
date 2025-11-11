// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    //alias(libs.plugins.firebase.crashlytics) apply false
}

// Update this block if you have it
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0") // Update to latest
        classpath("com.google.gms:google-services:4.4.0") // Update to latest
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}