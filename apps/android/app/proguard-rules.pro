# ARCore keyless authorization keep rules.
#
# The ARCore "Use the ARCore API on Google Cloud" guide for Android requires the
# Google Play services authentication libraries to be kept as-is when minifying:
#   https://developers.google.com/ar/develop/authorization?platform=android
# (section "Include required libraries"). Keyless auth authenticates through
# Google Play services using the app's package and signing certificate; R8 must
# not rename or strip the `com.google.android.gms.auth` package or its
# dependencies, otherwise the ARCore API returns ERROR_NOT_AUTHORIZED.

-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
