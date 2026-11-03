// AGP 9 has built-in Kotlin with a default Kotlin Gradle plugin version.
// Per the AGP 9.0 release notes, a newer KGP or KSP is chosen by putting it
// on the top-level buildscript classpath.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

// KSP is already on the classpath above, so modules apply it by id without a version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
