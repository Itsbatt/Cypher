# Cypher release rules

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations

# Room: generated implementations and entities are resolved by name
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.cypher.music.data.room.** { *; }

# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# YouTube IFrame bridge (JavaScript -> Java)
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# yt-dlp / FFmpeg native engine
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Optional OkHttp security providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# commons-compress registers extra fields via reflection
-keep class org.apache.commons.compress.** { *; }

# Google Code Scanner: 16.1.0 ships no consumer rules. R8 full mode (AGP 9)
# strips/rewrites its internal factories and GmsBarcodeScanning.getClient()
# then crashes with an NPE inside zzoi.create(). Keep the scanning internals.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }

# Optional XZ/7z codecs from commons-compress are not used by yt-dlp
-dontwarn org.tukaani.xz.**
