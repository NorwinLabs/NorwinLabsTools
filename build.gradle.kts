// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // The google-services plugin pulls an older JavaPoet onto the buildscript classpath, which
        // Hilt's AggregateDepsTask then fails against with NoSuchMethodError on
        // ClassName.canonicalName(). Forcing 1.13.0 here resolves the conflict for both plugins.
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
