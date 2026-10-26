# Keep Media3 classes and native methods
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep MPV Android Native Library & JNI methods
-keep class is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Keep models for reflection/serialization
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.PrimaryKey *;
}
