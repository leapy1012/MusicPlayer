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
# CouiAccentOverlay invokes this package-private Material API by its method name.
# Preserve only the reflection entry point; the rest of Material remains shrinkable.
-keepclassmembers class com.google.android.material.color.ColorResourcesLoaderCreator {
    static android.content.res.loader.ResourcesLoader create(android.content.Context, java.util.Map);
}

# Real compile-only framework headers expose BaseObj.guard as this hidden libcore type.
# DreamOS supplies it on the boot classpath; public SDK android.jar omits it.
# Suppress only this platform-library reference; do not bundle a replacement class.
-dontwarn dalvik.system.CloseGuard
