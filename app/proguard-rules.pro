# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.focuslock.data.local.entity.** { *; }

# Keep Retrofit interfaces
-keep interface com.focuslock.data.remote.api.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.focuslock.**$$serializer { *; }
-keepclassmembers class com.focuslock.** {
    *** Companion;
}
-keepclasseswithmembers class com.focuslock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Service classes
-keep class * extends android.app.Service
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.net.VpnService
-keep class * extends android.app.admin.DeviceAdminReceiver

# Keep all network DTOs — R8 must not rename JSON field names
-keep class com.focuslock.data.remote.dto.** { *; }

# Strip debug/verbose log calls in release builds (H-04)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Strip Timber log calls
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
