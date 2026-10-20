# kotlinx.serialization — pertahankan serializer yang dihasilkan plugin.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class id.diran.lisbon1944.**$$serializer { *; }
-keepclassmembers class id.diran.lisbon1944.** { *** Companion; }
-keepclasseswithmembers class id.diran.lisbon1944.** { kotlinx.serialization.KSerializer serializer(...); }

# File konten JSON dibaca dari resource Java milik modul :engine.
-keepdirectories content
