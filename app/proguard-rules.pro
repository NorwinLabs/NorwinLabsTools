# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so crash reports from released builds are readable, while still hiding the
# original file names. Without this a stack trace from a user is a list of obfuscated frames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebRTC (VoIP calling): the AAR ships its own consumer proguard.txt, but its native/JNI layer
# looks up Java classes and members by name, which R8 can't see through. This is a belt-and-
# suspenders rule on top of that, since minification can't be tested from this environment.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# JSch (SSH Client): loads ciphers, MACs and key-exchange implementations by class name from
# strings, so R8 sees no reference to them and strips them. The failure is a runtime
# ClassNotFoundException on connect, not a build error.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Google AI client (Idea/Video generators, Net Scanner analysis): serialises request and response
# models reflectively through kotlinx.serialization. Kept wholesale rather than by guessing at the
# model package, because the failure mode is a runtime serialisation error in a released build and
# minification cannot be exercised from this environment.
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# kotlinx.serialization: generated serializers are looked up reflectively from the companion.
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    public static ** Companion;
}

# osmdroid (all four map screens) reads configuration and tile-source classes reflectively.
-dontwarn org.osmdroid.**
