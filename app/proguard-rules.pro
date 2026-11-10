# Add project specific ProGuard rules here.

# Keep Kotlinx Serialization generated serializers and annotated model classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.lucianmarin.trax.**$$serializer { *; }
-keepclassmembers class com.lucianmarin.trax.** {
    *** Companion;
}
-keepclasseswithmembers class com.lucianmarin.trax.** {
    kotlinx.serialization.KSerializer serializer(...);
}

