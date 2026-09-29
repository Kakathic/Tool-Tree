# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in F:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Giữ lại toàn bộ package com.omarea và các package con
-keep class com.omarea.** { *; }

# Giữ lại toàn bộ package com.tool.tree và các package con
-keep class com.tool.tree.** { *; }

# Serializable
-keepclassmembers class * implements java.io.Serializable { *; }

# org.tomlj & dontwarn
-keep class org.tomlj.** { *; }
-dontwarn org.tomlj.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.common.**
