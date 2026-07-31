# ============================================================
# HHMusic — R8 / ProGuard rules
# ============================================================

# Keep Kotlin metadata and serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlinx.serialization.**

# kotlinx.serialization: keep serializers for @Serializable classes
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.hh.music.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hh.music.player.**$$serializer { *; }

# kotlinx.coroutines
-dontwarn kotlinx.coroutines.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp (ships its own consumer rules; keep just in case)
-dontwarn okhttp3.**
-dontwarn okio.**
