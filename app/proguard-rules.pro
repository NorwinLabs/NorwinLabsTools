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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# WebRTC (VoIP calling): the AAR ships its own consumer proguard.txt, but its native/JNI layer
# looks up Java classes and members by name, which R8 can't see through. This is a belt-and-
# suspenders rule on top of that, since minification can't be tested from this environment.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**