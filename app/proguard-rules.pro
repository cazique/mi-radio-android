# Media3 / ExoPlayer y Cast usan reflexión para instanciar extractores y renderers.
-keep class androidx.media3.** { *; }
-keep class com.google.android.gms.cast.** { *; }
-keep class androidx.mediarouter.** { *; }

# Modelos serializados a/desde JSON (kotlinx.serialization). OJO: el
# paquete "data.model" que estas reglas apuntaban antes nunca ha existido en
# este proyecto (las clases @Serializable están repartidas entre
# domain.model, data.remote, data.repository y util, y pueden acabar en
# cualquier otro paquete en el futuro); tal y como estaba, estas reglas no
# protegían nada en un build de release minificado (isMinifyEnabled =
# true), con riesgo de que R8 renombrara o eliminara los serializadores
# generados y rompiera el JSON del catálogo remoto, noticias, podcasts,
# tiempo, etc. en ese build. Se cubre todo el paquete de la app en vez de
# enumerar cada subpaquete, para no volver a desincronizarse si algo se
# mueve de sitio.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.miradio.app.**$$serializer { *; }
-keepclassmembers class com.miradio.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.miradio.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
