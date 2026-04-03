# WebDAV / Sardine
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class org.simpleframework.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.webdavmusic.**$$serializer { *; }
-keepclassmembers class com.webdavmusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.webdavmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Car App Library
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# General Android
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
