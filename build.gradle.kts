plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 has built-in Kotlin support: org.jetbrains.kotlin.android must not be applied.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}