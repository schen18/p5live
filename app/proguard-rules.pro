# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# This project uses a WebView with JS: AndroidBridge is exposed to JavaScript
# via addJavascriptInterface, so its members must not be stripped or renamed.
-keepclassmembers class com.dissonance.wfarer.p5js.p5editor.AndroidBridge {
   public *;
}
-keepattributes JavascriptInterface

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
