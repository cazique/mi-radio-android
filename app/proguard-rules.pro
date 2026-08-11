# Media3 / ExoPlayer y Cast usan reflexión para instanciar extractores y renderers.
-keep class androidx.media3.** { *; }
-keep class com.google.android.gms.cast.** { *; }
-keep class androidx.mediarouter.** { *; }

# Modelos serializados a/desde JSON (kotlinx.serialization)
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.miradio.app.data.model.**$$serializer { *; }
-keepclassmembers class com.miradio.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.miradio.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
