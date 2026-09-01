import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("com.google.gms.google-services")
}

// 1. Versioning Logic: Increments the version for RELEASE builds
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}

var verCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
var verName = versionProps.getProperty("VERSION_NAME", "1.0.0")

// Detect if we are running a release build task (prevents incrementing on debug runs)
val isBuildingRelease = gradle.startParameter.taskNames.any { 
    (it.contains("assembleRelease") || it.contains("bundleRelease")) && !it.contains("Debug")
}

if (isBuildingRelease) {
    verCode++
    val parts = verName.split(".").toMutableList()
    if (parts.isNotEmpty()) {
        val lastPart = parts.last().toIntOrNull() ?: 0
        parts[parts.size - 1] = (lastPart + 1).toString()
        verName = parts.joinToString(".")
    }
    
    // Save immediately so the APK and the file are in sync
    versionProps.setProperty("VERSION_CODE", verCode.toString())
    versionProps.setProperty("VERSION_NAME", verName)
    versionPropsFile.outputStream().use { versionProps.store(it, "Auto-incremented build version for Release") }
}

val buildTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

// 2. Release signing: resolved from keystore.properties locally, or environment variables on CI.
// The keystore itself is never committed - see keystore.properties.example.
class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val releaseSigning: ReleaseSigning? = run {
    val props = Properties()
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }

    fun value(property: String, environment: String): String? =
        props.getProperty(property)?.takeIf { it.isNotBlank() }
            ?: System.getenv(environment)?.takeIf { it.isNotBlank() }

    val path = value("storeFile", "KEYSTORE_FILE") ?: return@run null
    val store = rootProject.file(path)
    if (!store.exists()) return@run null

    ReleaseSigning(
        storeFile = store,
        storePassword = value("storePassword", "KEYSTORE_PASSWORD") ?: return@run null,
        keyAlias = value("keyAlias", "KEY_ALIAS") ?: return@run null,
        keyPassword = value("keyPassword", "KEY_PASSWORD") ?: return@run null,
    )
}

// A release build that silently ships unsigned is worse than one that fails, so say so loudly.
if (isBuildingRelease && releaseSigning == null) {
    logger.warn(
        "WARNING: no release signing configured. Set keystore.properties locally, or the " +
            "KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD environment variables on CI. " +
            "The release APK will be unsigned and cannot be installed as an update."
    )
}

// Room schemas are checked in so migrations can be written against a known previous version.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.norwinlabs.tools"
    // Compiling against 36 is required by current AndroidX (core-ktx 1.17, appcompat 1.7).
    // targetSdk stays at 35 deliberately: raising it changes runtime behaviour and belongs
    // with the UI work, not with a dependency bump.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.norwinlabstools"
        minSdk = 24
        targetSdk = 35
        versionCode = verCode
        versionName = verName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The release key is never stored in the repository. Locally it comes from
        // keystore.properties (gitignored, see keystore.properties.example); on CI it comes from
        // repository secrets via the environment. When neither is present the release build is
        // left unsigned rather than silently falling back to a shared key.
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword

                // Modern signing schemes, to avoid Google Play Protect warnings.
                enableV1Signing = true  // Legacy JAR signing for older devices
                enableV2Signing = true  // APK Signature Scheme v2 (Android 7.0+)
                enableV3Signing = true  // APK Signature Scheme v3 (Android 9.0+)
                enableV4Signing = true  // APK Signature Scheme v4 (Android 11+)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug uses the local SDK debug key. It deliberately no longer shares the release
            // key: that is what forced the release keystore to be committed in the first place.
            // No applicationIdSuffix here - google-services.json is keyed to the exact id.
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
        buildConfig = true
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

// Configuration Cache safe tasks
tasks.register("createBuildInfo") {
    group = "build"
    val propsFile = versionPropsFile
    val releaseDirFile = rootProject.layout.projectDirectory.dir("releases").asFile
    
    doLast {
        val props = Properties()
        if (propsFile.exists()) {
            propsFile.inputStream().use { props.load(it) }
        }
        val vName = props.getProperty("VERSION_NAME", "1.0.0")
        val vCode = props.getProperty("VERSION_CODE", "1")

        if (!releaseDirFile.exists()) releaseDirFile.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val infoFile = File(releaseDirFile, "latest-build-info.txt")
        infoFile.writeText("""
            NorwinLabsTools Build Information
            Version Name: ${"$"}${"{"}vName${"}"}
            Build Number: ${"$"}${"{"}vCode${"}"}
            Build Date:   ${"$"}${"{"}timestamp${"}"}
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
        finalizedBy("createBuildInfo")
        finalizedBy("copyApkToReleases")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.generativeai)
    implementation(libs.androidx.biometric)
    
    // OpenStreetMap
    implementation(libs.osmdroid)
    
    // Firebase
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)

    // Nearby Connections (P2P)
    implementation(libs.play.services.nearby)

    // Image Cropping
    implementation(libs.ucrop)

    // SSH Client
    implementation(libs.jsch)

    // VoIP Calling
    implementation(libs.stream.webrtc)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.fragment)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle / coroutines
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
