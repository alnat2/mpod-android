# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.mpod.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class androidx.room.paging.** { *; }
-dontwarn androidx.room.paging.**

# Hilt & Dagger
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-keep class com.example.mpod.MpodApplication_HiltComponents** { *; }
-keep class com.example.mpod.DaggerMpodApplication_HiltComponents** { *; }
-dontwarn dagger.hilt.**

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
