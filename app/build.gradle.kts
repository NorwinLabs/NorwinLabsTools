import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps["VERSION_CODE"] = "1"
    versionProps["VERSION_NAME"] = "1.0.0"
}

val verCode = versionProps["VERSION_CODE"].toString().toInt()
val verName = versionProps["VERSION_NAME"].toString()
val buildTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

android {
    namespace = "com.example.norwinlabstools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.norwinlabstools"
        minSdk = 24
        targetSdk = 35
        versionCode = verCode
        versionName = verName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Add signing configuration for Release builds
    signingConfigs {
        create("release") {
            // This is a temporary setup using debug credentials to ensure it installs
            // You can replace these with secrets later
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Enables R8 shrinking for smaller APKs
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Ensure debug builds don't have testOnly=true for easier sharing
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val fileName = "NorwinLabsTools-v${variant.versionName}-b${variant.versionCode}-${variant.name}.apk"
            output.outputFileName = fileName
        }
    }
}

tasks.register("incrementVersion") {
    group = "versioning"
    doLast {
        val props = Properties()
        if (versionPropsFile.exists()) {
            versionPropsFile.inputStream().use { props.load(it) }
        }
        val currentCode = props.getProperty("VERSION_CODE", "1").toInt()
        val nextCode = currentCode + 1
        props.setProperty("VERSION_CODE", nextCode.toString())
        
        val currentName = props.getProperty("VERSION_NAME", "1.0.0")
        val parts = currentName.split(".").toMutableList()
        if (parts.isNotEmpty()) {
            val lastPart = parts.last().toIntOrNull() ?: 0
            parts[parts.size - 1] = (lastPart + 1).toString()
            val nextName = parts.joinToString(".")
            props.setProperty("VERSION_NAME", nextName)
        }
        versionPropsFile.outputStream().use { props.store(it, "Auto-incremented build version") }
    }
}

tasks.register("createBuildInfo") {
    group = "build"
    val releaseDirFile = rootProject.layout.projectDirectory.dir("releases").asFile
    doLast {
        if (!releaseDirFile.exists()) releaseDirFile.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val infoFile = File(releaseDirFile, "latest-build-info.txt")
        infoFile.writeText("""
            NorwinLabsTools Build Information
            Version Name: ${verName}
            Build Number: ${verCode}
            Build Date:   ${timestamp}
        """.trimIndent())
    }
}

tasks.register<Copy>("copyApkToReleases") {
    group = "build"
    from(layout.buildDirectory.dir("outputs/apk"))
    into(rootProject.layout.projectDirectory.dir("releases"))
    include("**/*.apk")
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    eachFile { path = name }
}

tasks.configureEach {
    if (name.startsWith("assemble")) {
        dependsOn("incrementVersion")
        finalizedBy("createBuildInfo")
        finalizedBy("copyApkToReleases")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.generativeai)
    implementation(libs.androidx.biometric)
    implementation(libs.clarity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}