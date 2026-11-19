# CityFix ProGuard / R8 rules

# Keep Firestore model classes (they use reflection via toObject)
-keep class com.example.cityfixmaas.data.model.** { *; }

# Keep kotlinx.serialization (used by Navigation Compose type-safe routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.cityfixmaas.**$$serializer { *; }
-keepclassmembers class com.example.cityfixmaas.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.cityfixmaas.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Navigation route classes
-keep class com.example.cityfixmaas.ui.navigation.*Route { *; }
-keep class com.example.cityfixmaas.ui.navigation.*Route$* { *; }
